package com.xianhua.papercheck.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_settings")
public class UserSetting {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String defaultStyle;
    private Boolean enableRag;
    private Integer highRiskThreshold;
    private Integer suggestionCount;
    private String llmBaseUrl;
    private String llmModel;
    private String llmApiKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
