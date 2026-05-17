package com.xianhua.papercheck.service;

import com.xianhua.papercheck.common.BusinessException;
import com.xianhua.papercheck.entity.UserSetting;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PythonAssistantClient {
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PythonAssistantClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.ai-service.base-url:http://localhost:8090}") String baseUrl
    ) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofMinutes(5))
                .build();
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    public AssistantPythonResponse chat(
            Long userId,
            Long sessionId,
            String message,
            MultipartFile image,
            UserSetting setting,
            List<Map<String, String>> history
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("sessionId", sessionId);
        body.put("message", message == null ? "" : message);
        body.put("history", history);
        body.put("llmBaseUrl", nullToEmpty(setting.getLlmBaseUrl()));
        body.put("llmApiKey", nullToEmpty(setting.getLlmApiKey()));
        body.put("llmModel", nullToEmpty(setting.getLlmModel()));

        if (image != null && !image.isEmpty()) {
            try {
                body.put("imageName", image.getOriginalFilename());
                body.put("imageContentType", image.getContentType());
                body.put("imageBase64", Base64.getEncoder().encodeToString(image.getBytes()));
            } catch (IOException exception) {
                throw new BusinessException("图片读取失败");
            }
        }

        Map<?, ?> response = restTemplate.postForObject(baseUrl + "/assistant/chat", body, Map.class);
        if (response == null) {
            throw new BusinessException("Python AI 助手暂时没有响应");
        }
        return new AssistantPythonResponse(
                stringValue(response, "answer"),
                stringValue(response, "model"),
                stringValue(response, "imageInfo"),
                listValue(response, "ragReferences")
        );
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8090";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String stringValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static List<String> listValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public record AssistantPythonResponse(
            String answer,
            String model,
            String imageInfo,
            List<String> ragReferences
    ) {
    }
}
