package com.staranaly.arbiter.share;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staranaly.arbiter.model.ModelConversationMessage;
import com.staranaly.arbiter.model.ModelGateway;
import com.staranaly.arbiter.model.ModelReply;
import com.staranaly.arbiter.model.ModelServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class XiaohongshuDraftService {

    private static final long MAX_IMAGE_BYTES = 20_000_000L;
    private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(90);
    private static final String SYSTEM_PROMPT = """
            你是小红书图文笔记助手。仔细识别用户上传的图片，并严格按用户的文字要求创作。
            只描述图片中能确认的内容；看不清的细节不要猜测，不编造品牌、价格、地点或效果。
            输出简体中文 JSON 对象，且只能包含 title、body、hashtags 三个字段。
            title 是适合图文笔记的简短标题，body 是有自然段的发布正文，hashtags 是 3 至 8 个不带 # 的话题字符串。
            不要输出 Markdown 代码块、解释或发布承诺。
            """;

    private final ModelGateway modelGateway;
    private final ObjectMapper objectMapper;

    public XiaohongshuDraftService(ModelGateway modelGateway, ObjectMapper objectMapper) {
        this.modelGateway = modelGateway;
        this.objectMapper = objectMapper;
    }

    public XiaohongshuDraftResponse generate(MultipartFile image, String requirements) {
        String prompt = requirements == null ? "" : requirements.strip();
        if (prompt.isEmpty() || prompt.length() > 2000) {
            throw new XiaohongshuInputException("请填写不超过 2000 字的发布要求。");
        }
        if (image == null || image.isEmpty() || image.getSize() > MAX_IMAGE_BYTES) {
            throw new XiaohongshuInputException("请选择一张不超过 20 MB 的图片。");
        }

        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException exception) {
            throw new XiaohongshuInputException("无法读取上传的图片，请重新选择。");
        }
        String mimeType = detectMimeType(bytes);
        if (mimeType == null) {
            throw new XiaohongshuInputException("图片仅支持 JPEG、PNG、GIF 或 WebP 格式。");
        }

        ModelReply reply = modelGateway.next(List.of(
                ModelConversationMessage.text("system", SYSTEM_PROMPT),
                ModelConversationMessage.imagePrompt("发布要求：\n" + prompt, bytes, mimeType)),
                false, MODEL_TIMEOUT);
        if (reply.content() == null || !reply.toolCalls().isEmpty()) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型未返回可用的发布文案。");
        }
        return parseDraft(reply.content());
    }

    private XiaohongshuDraftResponse parseDraft(String response) {
        String content = response.strip();
        if (content.startsWith("```")) {
            int start = content.indexOf('\n');
            int end = content.lastIndexOf("```");
            if (start < 0 || end <= start) {
                throw invalidDraft();
            }
            content = content.substring(start + 1, end).strip();
        }
        try {
            JsonNode draft = objectMapper.readTree(content);
            if (draft == null || !draft.isObject()) {
                throw invalidDraft();
            }
            String title = draft.path("title").asText("").strip();
            String body = draft.path("body").asText("").strip();
            JsonNode rawTags = draft.path("hashtags");
            if (title.isEmpty() || title.length() > 100 || body.isEmpty() || body.length() > 5000
                    || !rawTags.isArray() || rawTags.size() > 8) {
                throw invalidDraft();
            }
            List<String> hashtags = new ArrayList<>();
            for (JsonNode rawTag : rawTags) {
                if (!rawTag.isTextual()) {
                    throw invalidDraft();
                }
                String tag = rawTag.asText().strip().replaceFirst("^#+", "").strip();
                if (!tag.isEmpty() && tag.length() <= 30 && !hashtags.contains(tag)) {
                    hashtags.add(tag);
                }
            }
            return new XiaohongshuDraftResponse(title, body, List.copyOf(hashtags));
        } catch (JsonProcessingException exception) {
            throw invalidDraft();
        }
    }

    private static ModelServiceException invalidDraft() {
        return new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型返回的文案格式不正确，请重新生成。");
    }

    private static String detectMimeType(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
            return "image/png";
        }
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') {
            return "image/gif";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
