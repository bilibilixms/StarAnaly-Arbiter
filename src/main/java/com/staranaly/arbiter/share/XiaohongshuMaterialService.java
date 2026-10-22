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
public class XiaohongshuMaterialService {
    private static final int MAX_CONTENT_LENGTH = 12_000;
    private static final int MAX_NAME_LENGTH = 60;
    private final XiaohongshuMaterialRepository repository;
    private final KnowledgeDocumentParser parser;

    public XiaohongshuMaterialService(XiaohongshuMaterialRepository repository, KnowledgeDocumentParser parser) {
        this.repository = repository;
        this.parser = parser;
    }

    public List<XiaohongshuMaterialResponse> list() {
        return repository.findAllByOrderByUpdatedAtDescIdDesc().stream()
                .map(XiaohongshuMaterialResponse::from).toList();
    }

    @Transactional
    public XiaohongshuMaterialResponse create(String name, String content, MultipartFile file) {
        String checkedName = checkName(name);
        MaterialText text = readContent(content, file, true);
        return XiaohongshuMaterialResponse.from(repository.save(
                new XiaohongshuMaterial(checkedName, text.content(), text.sourceName())));
    }

    @Transactional
    public XiaohongshuMaterialResponse update(long id, String name, String content, MultipartFile file) {
        XiaohongshuMaterial material = find(id);
        String checkedName = checkName(name);
        MaterialText text = readContent(content, file, false);
        if (text != null) {
            material.replace(checkedName, text.content(), text.sourceName());
        } else {
            material.replace(checkedName, material.getContent(), material.getSourceName());
        }
        return XiaohongshuMaterialResponse.from(material);
    }

    @Transactional
    public void delete(long id) {
        repository.delete(find(id));
    }

    public List<XiaohongshuMaterial> selected(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        if (ids.size() > 3 || ids.stream().anyMatch(id -> id == null || id <= 0)
                || ids.stream().distinct().count() != ids.size()) {
            throw new XiaohongshuInputException("每次最多选择 3 份不同的创作素材。");
        }
        List<XiaohongshuMaterial> found = repository.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new XiaohongshuInputException("选用的创作素材已不存在，请刷新后重试。");
        }
        return ids.stream().map(id -> found.stream().filter(item -> item.getId().equals(id)).findFirst().orElseThrow())
                .toList();
    }

    private XiaohongshuMaterial find(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new XiaohongshuInputException("创作素材不存在，请刷新后重试。"));
    }

    private static String checkName(String name) {
        String value = name == null ? "" : name.strip();
        if (value.isEmpty() || value.length() > MAX_NAME_LENGTH) {
            throw new XiaohongshuInputException("素材名称需为 1 至 60 字。");
        }
        return value;
    }

    private MaterialText readContent(String content, MultipartFile file, boolean required) {
        if (file != null && !file.isEmpty()) {
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isBlank() || fileName.length() > 255) {
                throw new XiaohongshuInputException("文档名称无效。");
            }
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".pdf") && !lower.endsWith(".doc") && !lower.endsWith(".docx")) {
                throw new XiaohongshuInputException("创作素材仅支持 PDF、DOC、DOCX 文档。");
            }
            if (file.getSize() > KnowledgeDocumentParser.MAX_BINARY_BYTES) {
                throw new XiaohongshuInputException("文档不能超过 5 MiB。");
            }
            try {
                return new MaterialText(checkContent(parser.parse(fileName, file.getBytes())), fileName);
            } catch (IOException exception) {
                throw new XiaohongshuInputException("无法读取文档，请重新选择。");
            } catch (KnowledgeInputException exception) {
                throw new XiaohongshuInputException(exception.getMessage());
            }
        }
        if (content != null) return new MaterialText(checkContent(content), null);
        if (required) throw new XiaohongshuInputException("请输入素材内容或上传文档。");
        return null;
    }

    private static String checkContent(String content) {
        String value = content.strip();
        if (value.isEmpty() || value.length() > MAX_CONTENT_LENGTH) {
            throw new XiaohongshuInputException("素材内容需为 1 至 12000 字；较长文档请摘选最有用的内容。");
        }
        return value;
    }

    private record MaterialText(String content, String sourceName) {}
}
