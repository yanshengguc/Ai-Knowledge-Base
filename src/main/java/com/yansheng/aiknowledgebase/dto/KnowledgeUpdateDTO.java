package com.yansheng.aiknowledgebase.dto;

import lombok.Data;

@Data
public class KnowledgeUpdateDTO {
    private String title;
    private String content;
    private String category;
    private String author;

}
