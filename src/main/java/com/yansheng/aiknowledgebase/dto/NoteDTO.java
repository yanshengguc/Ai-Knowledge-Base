package com.yansheng.aiknowledgebase.dto;

import lombok.Getter;
import lombok.Setter;

/** 新建笔记请求(写优先) */
@Setter
@Getter
public class NoteDTO {
    private String title;
    private String content;
    /** 来源标记:普通笔记不传;AI 对话保存传 "ai-chat"(前端显示 AI 徽标,防自增强循环可追溯) */
    private String source;
}
