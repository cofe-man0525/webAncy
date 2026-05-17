package com.xianhua.papercheck.service;

import com.xianhua.papercheck.entity.AnalysisTask;
import com.xianhua.papercheck.mapper.AnalysisTaskMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class PythonAgentClient {
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final AnalysisTaskMapper analysisTaskMapper;

    public PythonAgentClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.ai-service.base-url:http://localhost:8090}") String baseUrl,
            AnalysisTaskMapper analysisTaskMapper
    ) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofMinutes(10))
                .build();
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.analysisTaskMapper = analysisTaskMapper;
    }

    @Async("analysisExecutor")
    public void runTask(Long taskId) {
        try {
            restTemplate.postForObject(baseUrl + "/tasks/" + taskId + "/run", null, String.class);
        } catch (Exception exception) {
            markFailed(taskId, exception);
            throw new IllegalStateException("Python AI service call failed", exception);
        }
    }

    private void markFailed(Long taskId, Exception exception) {
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setStatus("failed");
        task.setProgress(100);
        task.setErrorMessage("Python AI service call failed: " + exception.getMessage());
        task.setUpdatedAt(LocalDateTime.now());
        task.setFinishedAt(LocalDateTime.now());
        analysisTaskMapper.updateById(task);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8090";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
