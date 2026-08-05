package com.yansheng.aiknowledgebase.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class FileEntity {
    private Long id;
    private Long userId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String fileUrl;
    private Long knowledgeId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String status;
}
