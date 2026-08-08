package com.yansheng.aiknowledgebase.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class KnowledgeDetailVO {
    private Long id;
    private String title;
    private String content;
    private String author;
    private String nickname;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
