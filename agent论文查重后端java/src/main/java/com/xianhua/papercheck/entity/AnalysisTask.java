package com.xianhua.papercheck.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("analysis_tasks")
public class AnalysisTask {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long paperId;
    private String status;
    private Integer progress;
    private String depth;
    private String style;
    private Boolean enableRag;
    private Integer suggestionCount;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;
}
