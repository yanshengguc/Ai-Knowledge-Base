package com.yansheng.aiknowledgebase.dto;

import lombok.Getter;
import lombok.Setter;

/** 新建笔记请求(写优先) */
@Setter
@Getter
public class NoteDTO {
    private String title;
    private String content;
}
