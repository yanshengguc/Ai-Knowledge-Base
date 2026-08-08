package com.yansheng.aiknowledgebase.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
@Setter
@Getter
public class ChunkEntity {

    private Long id;

    private Long fileId;

    private Integer chunkIndex;

    private String content;

    private Integer contentLength;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
