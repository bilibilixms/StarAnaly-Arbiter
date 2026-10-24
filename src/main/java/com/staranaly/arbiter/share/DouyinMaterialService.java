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
public class DouyinMaterialService {
    private static final int MAX_CONTENT_LENGTH = 12_000;
    private static final int MAX_NAME_LENGTH = 60;
    private final DouyinMaterialRepository repository;
    private final KnowledgeDocumentParser parser;

    public DouyinMaterialService(DouyinMaterialRepository repository, KnowledgeDocumentParser parser) {
        this.repository = repository;
        this.parser = parser;
    }

    public List<DouyinMaterialResponse> list() {
        return repository.findAllByOrderByUpdatedAtDescIdDesc().stream()
                .map(DouyinMaterialResponse::from).toList();
    }

    @Transactional
    public DouyinMaterialResponse create(String name, String content, MultipartFile file) {
        String checkedName = checkName(name);
        MaterialText text = readContent(content, file, true);
        return DouyinMaterialResponse.from(repository.save(
                new DouyinMaterial(checkedName, text.content(), text.sourceName())));
    }

    @Transactional
    public DouyinMaterialResponse update(long id, String name, String content, MultipartFile file) {
        DouyinMaterial material = find(id);
        String checkedName = checkName(name);
        MaterialText text = readContent(content, file, false);
        if (text != null) {
            material.replace(checkedName, text.content(), text.sourceName());
        } else {
            material.replace(checkedName, material.getContent(), material.getSourceName());
        }
        return DouyinMaterialResponse.from(material);
    }

    @Transactional
    public void delete(long id) {
        repository.delete(find(id));
    }

    public List<DouyinMaterial> selected(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        if (ids.size() > 3 || ids.stream().anyMatch(id -> id == null || id <= 0)
                || ids.stream().distinct().count() != ids.size()) {
            throw new DouyinInputException("每次最多选择 3 份不同的创作素材。");
        }
        List<DouyinMaterial> found = repository.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new DouyinInputException("选用的创作素材已不存在，请刷新后重试。");
        }
        return ids.stream().map(id -> found.stream().filter(item -> item.getId().equals(id)).findFirst().orElseThrow())
                .toList();
    }

    private DouyinMaterial find(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new DouyinInputException("创作素材不存在，请刷新后重试。"));
    }

    private static String checkName(String name) {
        String value = name == null ? "" : name.strip();
        if (value.isEmpty() || value.length() > MAX_NAME_LENGTH) {
            throw new DouyinInputException("素材名称需为 1 至 60 字。");
        }
        return value;
    }

    private MaterialText readContent(String content, MultipartFile file, boolean required) {
        if (file != null && !file.isEmpty()) {
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isBlank() || fileName.length() > 255) {
                throw new DouyinInputException("文档名称无效。");
            }
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".pdf") && !lower.endsWith(".doc") && !lower.endsWith(".docx")) {
                throw new DouyinInputException("创作素材仅支持 PDF、DOC、DOCX 文档。");
            }
            if (file.getSize() > KnowledgeDocumentParser.MAX_BINARY_BYTES) {
                throw new DouyinInputException("文档不能超过 5 MiB。");
            }
            try {
                return new MaterialText(checkContent(parser.parse(fileName, file.getBytes())), fileName);
            } catch (IOException exception) {
                throw new DouyinInputException("无法读取文档，请重新选择。");
            } catch (KnowledgeInputException exception) {
                throw new DouyinInputException(exception.getMessage());
            }
        }
        if (content != null) return new MaterialText(checkContent(content), null);
        if (required) throw new DouyinInputException("请输入素材内容或上传文档。");
        return null;
    }

    private static String checkContent(String content) {
        String value = content.strip();
        if (value.isEmpty() || value.length() > MAX_CONTENT_LENGTH) {
            throw new DouyinInputException("素材内容需为 1 至 12000 字；较长文档请摘选最有用的内容。");
        }
        return value;
    }

    private record MaterialText(String content, String sourceName) {}
}
