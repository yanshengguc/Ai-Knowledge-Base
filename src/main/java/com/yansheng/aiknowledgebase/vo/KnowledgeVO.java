package com.yansheng.aiknowledgebase.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class KnowledgeVO {
private  Long id;
    private String title;
    private String category;
    private String author;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
