package com.staranaly.arbiter.csv;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks/{taskId}/runs/{runId}/analysis")
public class CsvAnalysisController {

    private final CsvFileService service;

    public CsvAnalysisController(CsvFileService service) {
        this.service = service;
    }

    @GetMapping
    public List<CsvAnalysisResult> list(@PathVariable Long taskId, @PathVariable Long runId) {
        return service.analyses(taskId, runId);
    }
}
