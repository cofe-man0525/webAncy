package com.xianhua.papercheck.controller;

import com.xianhua.papercheck.common.ApiResponse;
import com.xianhua.papercheck.dto.AnalysisDtos;
import com.xianhua.papercheck.security.CurrentUser;
import com.xianhua.papercheck.service.AnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analysis")
public class AnalysisController {
    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/tasks/{taskId}/progress")
    public ApiResponse<AnalysisDtos.TaskProgressResponse> progress(@PathVariable Long taskId) {
        return ApiResponse.ok(analysisService.getProgress(CurrentUser.id(), taskId));
    }

    @GetMapping("/reports/{taskId}")
    public ApiResponse<AnalysisDtos.ReportResponse> report(@PathVariable Long taskId) {
        return ApiResponse.ok(analysisService.report(CurrentUser.id(), taskId));
    }

    @DeleteMapping("/reports/{taskId}")
    public ApiResponse<Void> deleteReport(@PathVariable Long taskId) {
        analysisService.deleteReport(CurrentUser.id(), taskId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/suggestions/regenerate")
    public ApiResponse<AnalysisDtos.RegenerateSuggestionResponse> regenerate(
            @RequestBody AnalysisDtos.RegenerateSuggestionRequest request
    ) {
        return ApiResponse.ok(analysisService.regenerate(CurrentUser.id(), request));
    }
}
