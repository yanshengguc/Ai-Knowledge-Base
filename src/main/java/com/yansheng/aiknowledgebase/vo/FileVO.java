package com.yansheng.aiknowledgebase.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
@Setter
@Getter
public class FileVO {
        private Long id;
        private String fileName;
        private Long fileSize;
        private String fileUrl;
        private Long knowledgeId;
        private String status;
        /** 文件 MIME 类型;笔记来源编码于此(text/markdown;source=ai-chat|manual) */
        private String fileType;
        private LocalDateTime updateTime;


}
