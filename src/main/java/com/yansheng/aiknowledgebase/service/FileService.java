package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.vo.FileVO;
import org.apache.ibatis.annotations.Param;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileService
{
    FileEntity uploadFile(MultipartFile file, Long knowledgeId);
     FileVO getFileById(Long id);

    /** 按知识查文件列表(含处理状态) */
    List<FileVO> listByKnowledgeId(Long knowledgeId);

    /** 删除文件(级联删切片 + OSS 对象,含权限校验) */
    void deleteFile(Long id);
}
