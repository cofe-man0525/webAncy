package com.xianhua.papercheck.dto;

import java.util.List;

public class AnalysisDtos {
    public record UploadResponse(Long taskId, String status) {
    }

    public record TaskProgressResponse(
            Long taskId,
            String status,
            Integer progress,
            String errorMessage
    ) {
    }

    public record HistoryItem(
            Long id,
            String title,
            String createdAt,
            String status,
            Integer score
    ) {
    }

    public record MemoryReportItem(
            Long taskId,
            String title,
            Integer score,
            String summary,
            String createdAt
    ) {
    }

    public record RiskReasonStat(
            String reason,
            Long count
    ) {
    }

    public record UserAnalysisMemoryResponse(
            Long userId,
            Integer reportCount,
            Integer averageRiskScore,
            List<MemoryReportItem> recentReports,
            List<RiskReasonStat> commonReasons
    ) {
    }

    public record AgentTraceItem(String name, String status, String detail) {
    }

    public record SentenceResult(
            Long id,
            String text,
            Integer riskScore,
            String riskLevel,
            List<String> reasons,
            String advice,
            List<String> suggestedTexts,
            List<String> ragReferences
    ) {
    }

    public record ParagraphResult(
            String id,
            Integer index,
            Integer riskScore,
            String text,
            List<SentenceResult> sentences
    ) {
    }

    public record ReportResponse(
            Long taskId,
            String title,
            Integer overallRiskScore,
            String summary,
            List<AgentTraceItem> agentTrace,
            List<ParagraphResult> paragraphs
    ) {
    }

    public record RegenerateSuggestionRequest(
            Long sentenceId,
            String style,
            Integer suggestionCount
    ) {
    }

    public record RegenerateSuggestionResponse(
            Long sentenceId,
            List<String> suggestedTexts
    ) {
    }
}
