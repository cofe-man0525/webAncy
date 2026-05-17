package com.xianhua.papercheck.controller;

import com.xianhua.papercheck.common.ApiResponse;
import com.xianhua.papercheck.dto.AnalysisDtos;
import com.xianhua.papercheck.security.CurrentUser;
import com.xianhua.papercheck.service.AnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/history")
public class HistoryController {
    private final AnalysisService analysisService;

    public HistoryController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/tasks")
    public ApiResponse<List<AnalysisDtos.HistoryItem>> tasks() {
        return ApiResponse.ok(analysisService.history(CurrentUser.id()));
    }

    @GetMapping("/memory")
    public ApiResponse<AnalysisDtos.UserAnalysisMemoryResponse> memory() {
        return ApiResponse.ok(analysisService.memory(CurrentUser.id()));
    }
}
