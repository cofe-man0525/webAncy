package com.xianhua.papercheck.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("papers")
public class Paper {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private String storagePath;
    private String fileHash;
    private LocalDateTime createdAt;
}
