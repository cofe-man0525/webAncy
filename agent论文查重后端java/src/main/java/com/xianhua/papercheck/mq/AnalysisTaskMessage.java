package com.xianhua.papercheck.mq;

public record AnalysisTaskMessage(
        Long taskId,
        Long userId,
        Long paperId,
        String storagePath,
        String depth,
        String style,
        Boolean enableRag,
        Integer suggestionCount,
        String llmBaseUrl,
        String llmApiKey,
        String llmModel
) {
}
