package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.entity.FileEntity;
import org.springframework.web.multipart.MultipartFile;

public interface FileService
{
    FileEntity uploadFile(MultipartFile file, Long knowledgeId);
}
