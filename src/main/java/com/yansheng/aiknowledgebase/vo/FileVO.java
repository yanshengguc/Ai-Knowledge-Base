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
        private LocalDateTime updateTime;


}
