package com.yansheng.aiknowledgebase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KnowledgeUpdateDTO {
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过 200 个字符")
    private String title;
    @NotBlank(message = "内容不能为空")
    private String content;
    @Size(max = 50, message = "分类长度不能超过 50 个字符")
    private String category;

}
