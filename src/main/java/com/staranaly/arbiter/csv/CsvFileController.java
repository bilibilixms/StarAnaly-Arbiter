package com.staranaly.arbiter.csv;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tasks/{taskId}/files")
public class CsvFileController {

    private final CsvFileService service;

    public CsvFileController(CsvFileService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvFileMetadata> upload(@PathVariable Long taskId,
            @RequestParam("file") MultipartFile file) {
        CsvFileMetadata saved = service.upload(taskId, file);
        return ResponseEntity.created(URI.create("/api/tasks/" + taskId + "/files/" + saved.id()))
                .body(saved);
    }

    @GetMapping
    public List<CsvFileMetadata> list(@PathVariable Long taskId) {
        return service.list(taskId);
    }

    @GetMapping("/{fileId}")
    public CsvPreviewResponse preview(@PathVariable Long taskId, @PathVariable Long fileId) {
        return service.preview(taskId, fileId, 20);
    }

    @GetMapping("/{fileId}/rows")
    public List<Map<String, String>> rows(@PathVariable Long taskId, @PathVariable Long fileId,
            @RequestParam(required = false) String filterColumn,
            @RequestParam(required = false) String filterValue,
            @RequestParam(defaultValue = "100") int limit) {
        return service.rows(taskId, fileId, filterColumn, filterValue, limit);
    }

    @GetMapping("/{fileId}/aggregate")
    public CsvAggregateResponse aggregate(@PathVariable Long taskId, @PathVariable Long fileId,
            @RequestParam String groupBy, @RequestParam String sumColumn,
            @RequestParam(defaultValue = "false") boolean byMonth,
            @RequestParam(required = false) String filterColumn,
            @RequestParam(required = false) String filterValue) {
        return service.aggregate(taskId, fileId, groupBy, sumColumn, byMonth, filterColumn, filterValue);
    }
}
