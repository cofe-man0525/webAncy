package com.xianhua.papercheck.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("analysis_reports")
public class AnalysisReport {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private Long userId;
    private Long paperId;
    private String title;
    private Integer overallRiskScore;
    private String summary;
    private String agentTraceJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
