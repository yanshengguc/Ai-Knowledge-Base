package com.yansheng.aiknowledgebase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 新建笔记请求(写优先) */
@Setter
@Getter
public class NoteDTO {
    @NotBlank(message = "笔记标题不能为空")
    @Size(max = 255, message = "笔记标题长度不能超过 255 个字符")
    private String title;
    @NotBlank(message = "笔记内容不能为空")
    private String content;
    /** 来源标记:普通笔记不传;AI 对话保存传 "ai-chat"(前端显示 AI 徽标,防自增强循环可追溯) */
    private String source;
}
