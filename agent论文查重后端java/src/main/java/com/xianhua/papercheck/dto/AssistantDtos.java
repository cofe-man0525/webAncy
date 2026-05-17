package com.xianhua.papercheck.dto;

import java.util.List;

public class AssistantDtos {
    public record ChatSessionItem(
            Long id,
            String title,
            String updatedAt
    ) {
    }

    public record ChatMessageItem(
            Long id,
            Long sessionId,
            String role,
            String content,
            String imageName,
            String imageInfo,
            String model,
            String createdAt
    ) {
    }

    public record ChatResponse(
            Long sessionId,
            ChatMessageItem userMessage,
            ChatMessageItem assistantMessage,
            List<String> ragReferences
    ) {
    }
}
