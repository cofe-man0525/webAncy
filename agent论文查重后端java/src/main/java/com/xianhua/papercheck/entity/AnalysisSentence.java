package com.xianhua.papercheck.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("analysis_sentences")
public class AnalysisSentence {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long reportId;
    private Long taskId;
    private Integer paragraphIndex;
    private Integer sentenceIndex;
    private String originalText;
    private Integer riskScore;
    private String riskLevel;
    private String reasonsJson;
    private String advice;
    private String suggestedTextsJson;
    private String ragReferencesJson;
    private LocalDateTime createdAt;
}
