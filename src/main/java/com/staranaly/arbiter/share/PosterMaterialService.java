package com.staranaly.arbiter.share;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import com.staranaly.arbiter.knowledge.KnowledgeDocumentParser;
import com.staranaly.arbiter.knowledge.KnowledgeInputException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PosterMaterialService {
    private static final int MAX_NAME_LENGTH = 60;
    private static final int MAX_TEXT_LENGTH = 12_000;
    private static final int MAX_IMAGE_NOTE_LENGTH = 1_000;
    private static final int MAX_IMAGE_BYTES = 10_000_000;
    private final PosterMaterialRepository repository;
    private final KnowledgeDocumentParser parser;

    public PosterMaterialService(PosterMaterialRepository repository, KnowledgeDocumentParser parser) {
        this.repository = repository;
        this.parser = parser;
    }

    public List<PosterMaterialResponse> list() {
        return repository.listSummaries();
    }

    @Transactional
    public PosterMaterialResponse create(String name, PosterMaterial.Type type, String content, MultipartFile file) {
        if (type == null) throw new XiaohongshuInputException("请选择素材类型。");
        PosterMaterial material;
        if (type == PosterMaterial.Type.TEXT) {
            TextSource text = readText(content, file, true);
            material = new PosterMaterial(checkName(name), type, text.content(), text.sourceName(), null, null);
        } else {
            ImageSource image = readImage(file, true);
            material = new PosterMaterial(checkName(name), type, checkImageNote(content), image.name(), image.mime(), image.bytes());
        }
        return PosterMaterialResponse.from(repository.save(material));
    }

    @Transactional
    public PosterMaterialResponse update(long id, String name, String content, MultipartFile file) {
        PosterMaterial material = find(id);
        if (material.getType() == PosterMaterial.Type.TEXT) {
            TextSource text = readText(content, file, false);
            material.replace(checkName(name), material.getType(), text == null ? material.getContent() : text.content(),
                    text == null ? material.getSourceName() : text.sourceName(), null, null);
        } else {
            ImageSource image = readImage(file, false);
            material.replace(checkName(name), material.getType(),
                    content == null ? material.getContent() : checkImageNote(content),
                    image == null ? material.getSourceName() : image.name(),
                    image == null ? material.getMime() : image.mime(),
                    image == null ? material.getImageData() : image.bytes());
        }
        return PosterMaterialResponse.from(material);
    }

    @Transactional
    public void delete(long id) { repository.delete(find(id)); }

    public PosterMaterial image(long id) {
        PosterMaterial material = find(id);
        if (material.getType() == PosterMaterial.Type.TEXT) throw new XiaohongshuInputException("该素材不是图片。");
        return material;
    }

    public List<PosterMaterial> selected(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        if (ids.size() > 6 || ids.stream().anyMatch(id -> id == null || id <= 0)
                || ids.stream().distinct().count() != ids.size()) {
            throw new XiaohongshuInputException("每次最多选择 6 份不同的海报素材。");
        }
        List<PosterMaterial> found = repository.findAllById(ids);
        if (found.size() != ids.size()) throw new XiaohongshuInputException("选用的海报素材已不存在，请刷新后重试。");
        List<PosterMaterial> ordered = ids.stream()
                .map(id -> found.stream().filter(item -> item.getId().equals(id)).findFirst().orElseThrow()).toList();
        if (ordered.stream().filter(item -> item.getType() == PosterMaterial.Type.REFERENCE_IMAGE).count() > 3) {
            throw new XiaohongshuInputException("每次最多选择 3 张视觉参考图。");
        }
        return ordered;
    }

    private PosterMaterial find(long id) {
        return repository.findById(id).orElseThrow(() -> new XiaohongshuInputException("海报素材不存在，请刷新后重试。"));
    }

    private static String checkName(String name) {
        String value = name == null ? "" : name.strip();
        if (value.isEmpty() || value.length() > MAX_NAME_LENGTH) {
            throw new XiaohongshuInputException("素材名称需为 1 至 60 字。");
        }
        return value;
    }

    private TextSource readText(String content, MultipartFile file, boolean required) {
        if (file != null && !file.isEmpty()) {
            String name = file.getOriginalFilename();
            if (name == null || name.isBlank() || name.length() > 255) throw new XiaohongshuInputException("文档名称无效。");
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".pdf") && !lower.endsWith(".doc") && !lower.endsWith(".docx")) {
                throw new XiaohongshuInputException("文字素材仅支持 PDF、DOC、DOCX 文档。");
            }
            if (file.getSize() > KnowledgeDocumentParser.MAX_BINARY_BYTES) {
                throw new XiaohongshuInputException("文档不能超过 5 MiB。");
            }
            try {
                return new TextSource(checkText(parser.parse(name, file.getBytes())), name);
            } catch (IOException exception) {
                throw new XiaohongshuInputException("无法读取文档，请重新选择。");
            } catch (KnowledgeInputException exception) {
                throw new XiaohongshuInputException(exception.getMessage());
            }
        }
        if (content != null) return new TextSource(checkText(content), null);
        if (required) throw new XiaohongshuInputException("请输入文字素材或上传文档。");
        return null;
    }

    private static String checkText(String content) {
        String value = content.strip();
        if (value.isEmpty() || value.length() > MAX_TEXT_LENGTH) {
            throw new XiaohongshuInputException("文字素材需为 1 至 12000 字；较长文档请摘选重点。");
        }
        return value;
    }

    private static String checkImageNote(String content) {
        String value = content == null ? "" : content.strip();
        if (value.length() > MAX_IMAGE_NOTE_LENGTH) throw new XiaohongshuInputException("图片使用说明不能超过 1000 字。");
        return value;
    }

    private static ImageSource readImage(MultipartFile file, boolean required) {
        if (file == null || file.isEmpty()) {
            if (required) throw new XiaohongshuInputException("请上传一张 Logo 或参考图。");
            return null;
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank() || name.length() > 255) throw new XiaohongshuInputException("图片名称无效。");
        if (file.getSize() > MAX_IMAGE_BYTES) throw new XiaohongshuInputException("图片不能超过 10 MB。");
        try {
            byte[] bytes = file.getBytes();
            String mime = XiaohongshuDraftService.detectMimeType(bytes);
            if (mime == null) throw new XiaohongshuInputException("图片仅支持 JPEG、PNG、GIF 或 WebP。");
            return new ImageSource(name, mime, bytes);
        } catch (IOException exception) {
            throw new XiaohongshuInputException("无法读取图片，请重新选择。");
        }
    }

    private record TextSource(String content, String sourceName) {}
    private record ImageSource(String name, String mime, byte[] bytes) {}
}
