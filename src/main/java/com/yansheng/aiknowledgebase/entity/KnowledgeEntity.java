package com.yansheng.aiknowledgebase.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class KnowledgeEntity {
    private Long id;
    private String title;
    private String content;
    private String category;
    private Long userId;
    private String author;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;


}
