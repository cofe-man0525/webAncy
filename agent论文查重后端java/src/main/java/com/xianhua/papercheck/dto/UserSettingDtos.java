package com.xianhua.papercheck.dto;

public class UserSettingDtos {
    public record SettingResponse(
            String defaultStyle,
            Boolean enableRag,
            Integer highRiskThreshold,
            Integer suggestionCount,
            String llmBaseUrl,
            String llmModel,
            Boolean llmApiKeyConfigured
    ) {
    }

    public record UpdateSettingRequest(
            String defaultStyle,
            Boolean enableRag,
            Integer highRiskThreshold,
            Integer suggestionCount,
            String llmBaseUrl,
            String llmModel,
            String llmApiKey,
            Boolean clearLlmApiKey
    ) {
    }
}
