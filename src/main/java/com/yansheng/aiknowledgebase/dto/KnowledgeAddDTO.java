package com.yansheng.aiknowledgebase.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class KnowledgeAddDTO {
    private String title;
    private String content;
    private String category;



}
