package com.xianhua.papercheck.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianhua.papercheck.common.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JsonService {
    private final ObjectMapper objectMapper;

    public JsonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException("JSON 序列化失败");
        }
    }

    public List<String> toStringList(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    public <T> List<T> toList(String json, TypeReference<List<T>> typeReference) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, typeReference);
        } catch (Exception exception) {
            return List.of();
        }
    }
}
