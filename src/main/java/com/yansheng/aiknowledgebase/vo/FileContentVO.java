package com.yansheng.aiknowledgebase.vo;

import lombok.Data;

/** B-112 文件在线预览 VO:md 文本类返回 content,pdf/docx 仅元信息(前端提示不支持) */
@Data
public class FileContentVO {
    private Long id;
    private String fileName;
    private String fileType;
    /** 文件原文(md 文本);非文本类为 null */
    private String content;
}
