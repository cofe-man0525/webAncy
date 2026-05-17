package com.xianhua.papercheck.service;

import com.xianhua.papercheck.dto.AnalysisDtos;
import com.xianhua.papercheck.entity.AnalysisReport;
import com.xianhua.papercheck.entity.AnalysisSentence;
import com.xianhua.papercheck.entity.AnalysisTask;
import com.xianhua.papercheck.entity.Paper;
import com.xianhua.papercheck.mapper.AnalysisReportMapper;
import com.xianhua.papercheck.mapper.AnalysisSentenceMapper;
import com.xianhua.papercheck.mapper.AnalysisTaskMapper;
import com.xianhua.papercheck.mapper.PaperMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MockAnalysisService {
    private final AnalysisTaskMapper taskMapper;
    private final PaperMapper paperMapper;
    private final AnalysisReportMapper reportMapper;
    private final AnalysisSentenceMapper sentenceMapper;
    private final JsonService jsonService;

    public MockAnalysisService(
            AnalysisTaskMapper taskMapper,
            PaperMapper paperMapper,
            AnalysisReportMapper reportMapper,
            AnalysisSentenceMapper sentenceMapper,
            JsonService jsonService
    ) {
        this.taskMapper = taskMapper;
        this.paperMapper = paperMapper;
        this.reportMapper = reportMapper;
        this.sentenceMapper = sentenceMapper;
        this.jsonService = jsonService;
    }

    @Async("analysisExecutor")
    public void runMockAnalysis(Long taskId) {
        AnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        try {
            updateProgress(task, "processing", 18, null);
            sleep(500);
            updateProgress(task, "processing", 45, null);
            sleep(500);
            updateProgress(task, "processing", 72, null);
            sleep(500);
            createReport(task);
            updateProgress(task, "done", 100, null);
        } catch (Exception exception) {
            updateProgress(task, "failed", 100, "模拟分析失败");
        }
    }

    private void createReport(AnalysisTask task) {
        Paper paper = paperMapper.selectById(task.getPaperId());
        LocalDateTime now = LocalDateTime.now();

        List<AnalysisDtos.AgentTraceItem> trace = List.of(
                new AnalysisDtos.AgentTraceItem("文档解析工具", "done", "已解析正文、标题、摘要与图片文字入口"),
                new AnalysisDtos.AgentTraceItem("句子切分工具", "done", "已按段落和句子建立分析单元"),
                new AnalysisDtos.AgentTraceItem("RAG 检索工具", "done", "已检索学术写作规范和领域表达参考"),
                new AnalysisDtos.AgentTraceItem("表达优化工具", "done", "已生成可复制的句子级参考表达")
        );

        AnalysisReport report = new AnalysisReport();
        report.setTaskId(task.getId());
        report.setUserId(task.getUserId());
        report.setPaperId(task.getPaperId());
        report.setTitle(cleanTitle(paper == null ? "未命名论文" : paper.getOriginalName()));
        report.setOverallRiskScore(68);
        report.setSummary("文章整体结构完整，但部分段落存在概括性强、句式模板化、缺少具体研究对象和证据支撑的问题。建议优先处理高风险句子，并补充真实研究过程、数据来源和案例细节。");
        report.setAgentTraceJson(jsonService.toJson(trace));
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        reportMapper.insert(report);

        insertSentence(report, task, 1, 1, "随着人工智能技术的不断发展，其在教育领域中的应用越来越广泛。", 76, "high",
                List.of("常见开头表达，概括性较强", "缺少具体教育场景或研究对象", "没有体现作者自己的研究切入点"),
                "建议把笼统背景改成具体研究场景，并说明文章关注的评价环节。",
                List.of("近年来，高校在课程评价、学习过程记录和教学反馈中逐步引入智能分析工具，使教学评价从期末结果判断转向过程性诊断。",
                        "在高校教学评价场景中，人工智能主要被用于学习行为识别、作业反馈和课堂参与度分析，这为评价方式的改进提供了新的技术基础。"),
                List.of("引言部分应明确研究对象、问题范围与具体应用场景。"));

        insertSentence(report, task, 1, 2, "本文旨在探讨人工智能技术在高校教学评价中的应用及其影响，并分析其未来发展趋势。", 88, "high",
                List.of("“本文旨在探讨”属于高频模板句", "研究对象、方法和材料不够清晰", "“未来发展趋势”范围过大"),
                "建议补充研究材料、分析维度和限定范围，让句子更像真实论文表述。",
                List.of("本文以高校课程评价中的学习数据分析为切入点，结合相关文献与案例，讨论智能技术对评价效率、反馈质量和教师决策方式的影响。",
                        "本研究聚焦高校教学评价中的过程性数据应用，重点分析智能反馈、学习预警和评价指标调整三个方面的实践价值。"),
                List.of("研究目的句应包含对象、方法或分析维度，避免只保留空泛动词。"));

        insertSentence(report, task, 2, 1, "在实际应用过程中，人工智能能够提高教学评价的效率和准确性，同时也可能带来数据隐私、算法偏见等问题。", 58, "medium",
                List.of("观点方向清楚，但证据支撑不足", "可以补充具体评价任务或案例"),
                "建议增加具体例子，说明效率和准确性体现在哪些任务中。",
                List.of("在课堂表现记录、作业反馈和学习预警等任务中，智能系统可以缩短教师整理评价信息的时间，但其判断结果仍可能受到数据来源和模型训练偏差的影响。"),
                List.of("讨论技术影响时，建议同时呈现应用场景与限制条件。"));

        insertSentence(report, task, 2, 2, "因此，需要从技术、制度和伦理等多个层面进行综合分析。", 70, "medium",
                List.of("总结句较泛化", "“多个层面”没有展开具体含义"),
                "建议把层面展开成与论文主题相关的具体分析对象。",
                List.of("因此，本文将从评价数据治理、算法透明度和教师使用责任三个方面讨论高校引入智能评价工具时需要关注的问题。"),
                List.of("章节过渡句应尽量提示后文结构，增强论文的可读性。"));

        insertSentence(report, task, 3, 1, "通过对三门公共课程的访谈记录进行整理可以发现，教师更关注系统反馈能否解释学生表现变化，而不仅仅是给出分数排序。", 39, "low",
                List.of("包含具体材料来源", "表达体现研究过程", "结论范围相对明确"),
                "该句风险较低，可保留。若有条件，可继续补充访谈对象数量。",
                List.of("通过整理三门公共课程的教师访谈记录可以发现，受访教师更关注系统反馈能否解释学生表现变化，而不仅仅是给出分数排序。"),
                List.of("低风险句通常具备具体材料、清晰对象和限定性结论。"));
    }

    private void insertSentence(
            AnalysisReport report,
            AnalysisTask task,
            Integer paragraphIndex,
            Integer sentenceIndex,
            String text,
            Integer riskScore,
            String riskLevel,
            List<String> reasons,
            String advice,
            List<String> suggestedTexts,
            List<String> ragReferences
    ) {
        AnalysisSentence sentence = new AnalysisSentence();
        sentence.setReportId(report.getId());
        sentence.setTaskId(task.getId());
        sentence.setParagraphIndex(paragraphIndex);
        sentence.setSentenceIndex(sentenceIndex);
        sentence.setOriginalText(text);
        sentence.setRiskScore(riskScore);
        sentence.setRiskLevel(riskLevel);
        sentence.setReasonsJson(jsonService.toJson(reasons));
        sentence.setAdvice(advice);
        sentence.setSuggestedTextsJson(jsonService.toJson(suggestedTexts));
        sentence.setRagReferencesJson(jsonService.toJson(ragReferences));
        sentence.setCreatedAt(LocalDateTime.now());
        sentenceMapper.insert(sentence);
    }

    private void updateProgress(AnalysisTask task, String status, int progress, String errorMessage) {
        task.setStatus(status);
        task.setProgress(progress);
        task.setErrorMessage(errorMessage);
        task.setUpdatedAt(LocalDateTime.now());
        if ("processing".equals(status) && task.getStartedAt() == null) {
            task.setStartedAt(LocalDateTime.now());
        }
        if ("done".equals(status) || "failed".equals(status)) {
            task.setFinishedAt(LocalDateTime.now());
        }
        taskMapper.updateById(task);
    }

    private String cleanTitle(String name) {
        return name.replaceAll("\\.(docx|doc|pdf|txt)$", "");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
