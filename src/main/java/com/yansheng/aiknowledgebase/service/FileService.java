package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.vo.FileVO;
import org.apache.ibatis.annotations.Param;
import org.springframework.web.multipart.MultipartFile;

public interface FileService
{
    FileEntity uploadFile(MultipartFile file, Long knowledgeId);
     FileVO getFileById(Long id);
}
