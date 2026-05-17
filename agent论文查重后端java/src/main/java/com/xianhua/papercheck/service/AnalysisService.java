package com.xianhua.papercheck.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xianhua.papercheck.common.BusinessException;
import com.xianhua.papercheck.dto.AnalysisDtos;
import com.xianhua.papercheck.entity.AnalysisReport;
import com.xianhua.papercheck.entity.AnalysisSentence;
import com.xianhua.papercheck.entity.AnalysisTask;
import com.xianhua.papercheck.entity.Paper;
import com.xianhua.papercheck.mapper.AnalysisReportMapper;
import com.xianhua.papercheck.mapper.AnalysisSentenceMapper;
import com.xianhua.papercheck.mapper.AnalysisTaskMapper;
import com.xianhua.papercheck.mapper.PaperMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalysisService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss");

    private final AnalysisTaskMapper taskMapper;
    private final AnalysisReportMapper reportMapper;
    private final AnalysisSentenceMapper sentenceMapper;
    private final PaperMapper paperMapper;
    private final JsonService jsonService;

    public AnalysisService(
            AnalysisTaskMapper taskMapper,
            AnalysisReportMapper reportMapper,
            AnalysisSentenceMapper sentenceMapper,
            PaperMapper paperMapper,
            JsonService jsonService
    ) {
        this.taskMapper = taskMapper;
        this.reportMapper = reportMapper;
        this.sentenceMapper = sentenceMapper;
        this.paperMapper = paperMapper;
        this.jsonService = jsonService;
    }

    public AnalysisDtos.TaskProgressResponse getProgress(Long userId, Long taskId) {
        AnalysisTask task = getOwnedTask(userId, taskId);
        return new AnalysisDtos.TaskProgressResponse(task.getId(), task.getStatus(), task.getProgress(), task.getErrorMessage());
    }

    public List<AnalysisDtos.HistoryItem> history(Long userId) {
        List<AnalysisTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getUserId, userId)
                .orderByDesc(AnalysisTask::getCreatedAt));
        return tasks.stream().map(task -> {
            Paper paper = paperMapper.selectById(task.getPaperId());
            AnalysisReport report = reportMapper.selectOne(new LambdaQueryWrapper<AnalysisReport>().eq(AnalysisReport::getTaskId, task.getId()));
            String title = paper == null ? "未命名论文" : paper.getOriginalName();
            Integer score = report == null ? null : report.getOverallRiskScore();
            return new AnalysisDtos.HistoryItem(
                    task.getId(),
                    title,
                    task.getCreatedAt() == null ? "" : task.getCreatedAt().format(DATE_TIME),
                    task.getStatus(),
                    score
            );
        }).toList();
    }

    public AnalysisDtos.UserAnalysisMemoryResponse memory(Long userId) {
        List<AnalysisReport> reports = reportMapper.selectList(new LambdaQueryWrapper<AnalysisReport>()
                .eq(AnalysisReport::getUserId, userId)
                .orderByDesc(AnalysisReport::getCreatedAt)
                .last("LIMIT 5"));

        int averageRiskScore = reports.isEmpty()
                ? 0
                : (int) Math.round(reports.stream()
                .mapToInt(AnalysisReport::getOverallRiskScore)
                .average()
                .orElse(0));

        List<AnalysisDtos.MemoryReportItem> recentReports = reports.stream()
                .map(report -> new AnalysisDtos.MemoryReportItem(
                        report.getTaskId(),
                        report.getTitle(),
                        report.getOverallRiskScore(),
                        report.getSummary(),
                        report.getCreatedAt() == null ? "" : report.getCreatedAt().format(DATE_TIME)
                ))
                .toList();

        List<Long> reportIds = reports.stream().map(AnalysisReport::getId).toList();
        Map<String, Long> reasonCounter = new HashMap<>();
        if (!reportIds.isEmpty()) {
            List<AnalysisSentence> sentences = sentenceMapper.selectList(new LambdaQueryWrapper<AnalysisSentence>()
                    .in(AnalysisSentence::getReportId, reportIds)
                    .ge(AnalysisSentence::getRiskScore, 75)
                    .orderByDesc(AnalysisSentence::getCreatedAt));
            for (AnalysisSentence sentence : sentences) {
                for (String reason : jsonService.toStringList(sentence.getReasonsJson())) {
                    reasonCounter.merge(reason, 1L, Long::sum);
                }
            }
        }

        List<AnalysisDtos.RiskReasonStat> commonReasons = reasonCounter.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> new AnalysisDtos.RiskReasonStat(entry.getKey(), entry.getValue()))
                .toList();

        return new AnalysisDtos.UserAnalysisMemoryResponse(
                userId,
                reports.size(),
                averageRiskScore,
                recentReports,
                commonReasons
        );
    }

    public AnalysisDtos.ReportResponse report(Long userId, Long taskId) {
        AnalysisTask task = getOwnedTask(userId, taskId);
        AnalysisReport report = reportMapper.selectOne(new LambdaQueryWrapper<AnalysisReport>().eq(AnalysisReport::getTaskId, task.getId()));
        if (report == null) {
            throw new BusinessException("报告尚未生成，请稍后再试");
        }

        List<AnalysisDtos.AgentTraceItem> trace = jsonService.toList(
                report.getAgentTraceJson(),
                new TypeReference<>() {
                }
        );
        List<AnalysisSentence> sentences = sentenceMapper.selectList(new LambdaQueryWrapper<AnalysisSentence>()
                .eq(AnalysisSentence::getReportId, report.getId())
                .orderByAsc(AnalysisSentence::getParagraphIndex)
                .orderByAsc(AnalysisSentence::getSentenceIndex));

        Map<Integer, List<AnalysisSentence>> grouped = sentences.stream()
                .collect(Collectors.groupingBy(AnalysisSentence::getParagraphIndex, LinkedHashMap::new, Collectors.toList()));

        List<AnalysisDtos.ParagraphResult> paragraphs = new ArrayList<>();
        for (Map.Entry<Integer, List<AnalysisSentence>> entry : grouped.entrySet()) {
            List<AnalysisSentence> group = entry.getValue();
            Integer riskScore = group.stream().map(AnalysisSentence::getRiskScore).max(Comparator.naturalOrder()).orElse(0);
            String text = group.stream().map(AnalysisSentence::getOriginalText).collect(Collectors.joining(""));
            List<AnalysisDtos.SentenceResult> sentenceResults = group.stream().map(this::toSentenceResult).toList();
            paragraphs.add(new AnalysisDtos.ParagraphResult(
                    "p" + entry.getKey(),
                    entry.getKey(),
                    riskScore,
                    text,
                    sentenceResults
            ));
        }

        return new AnalysisDtos.ReportResponse(
                task.getId(),
                report.getTitle(),
                report.getOverallRiskScore(),
                report.getSummary(),
                trace,
                paragraphs
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteReport(Long userId, Long taskId) {
        AnalysisTask task = getOwnedTask(userId, taskId);
        AnalysisReport report = reportMapper.selectOne(new LambdaQueryWrapper<AnalysisReport>()
                .eq(AnalysisReport::getTaskId, task.getId()));
        if (report != null) {
            sentenceMapper.delete(new LambdaQueryWrapper<AnalysisSentence>()
                    .eq(AnalysisSentence::getReportId, report.getId()));
            reportMapper.deleteById(report.getId());
        }
        taskMapper.deleteById(task.getId());
    }

    public AnalysisDtos.RegenerateSuggestionResponse regenerate(Long userId, AnalysisDtos.RegenerateSuggestionRequest request) {
        AnalysisSentence sentence = sentenceMapper.selectById(request.sentenceId());
        if (sentence == null) {
            throw new BusinessException("句子不存在");
        }
        getOwnedTask(userId, sentence.getTaskId());

        int count = request.suggestionCount() == null ? 2 : Math.max(1, Math.min(5, request.suggestionCount()));
        String style = request.style() == null ? "academic" : request.style();
        List<String> suggestions = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            suggestions.add(buildSuggestion(sentence.getOriginalText(), style, index));
        }
        sentence.setSuggestedTextsJson(jsonService.toJson(suggestions));
        sentenceMapper.updateById(sentence);
        return new AnalysisDtos.RegenerateSuggestionResponse(sentence.getId(), suggestions);
    }

    private AnalysisDtos.SentenceResult toSentenceResult(AnalysisSentence sentence) {
        return new AnalysisDtos.SentenceResult(
                sentence.getId(),
                sentence.getOriginalText(),
                sentence.getRiskScore(),
                sentence.getRiskLevel(),
                jsonService.toStringList(sentence.getReasonsJson()),
                sentence.getAdvice(),
                jsonService.toStringList(sentence.getSuggestedTextsJson()),
                jsonService.toStringList(sentence.getRagReferencesJson())
        );
    }

    private AnalysisTask getOwnedTask(Long userId, Long taskId) {
        AnalysisTask task = taskMapper.selectOne(new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getId, taskId)
                .eq(AnalysisTask::getUserId, userId));
        if (task == null) {
            throw new BusinessException("任务不存在或无权访问");
        }
        return task;
    }

    private String buildSuggestion(String originalText, String style, int index) {
        String styleText = switch (style) {
            case "natural" -> "更自然清晰";
            case "concise" -> "更简洁严谨";
            default -> "更正式学术";
        };
        return "参考表达 " + index + "：" + styleText + "地重写该句时，应补充具体研究对象、材料来源或分析维度，避免仅保留概括性判断。原句关注内容为：“" + originalText + "”";
    }
}
