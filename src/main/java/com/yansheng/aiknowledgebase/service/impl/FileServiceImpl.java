package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.common.BusinessException;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.DocumentService;
import com.yansheng.aiknowledgebase.service.FileService;
import com.yansheng.aiknowledgebase.service.HelloService;
import com.yansheng.aiknowledgebase.service.OssService;
import com.yansheng.aiknowledgebase.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
@Slf4j
@Service
public class FileServiceImpl implements FileService {

    private final DocumentService documentService;
    private final OssService ossService;
    private final KnowledgeMapper knowledgeMapper;
    private final FileMapper fileMapper;

    public FileServiceImpl(DocumentService documentService,
                           OssService ossService,
                           KnowledgeMapper knowledgeMapper,
                           FileMapper fileMapper) {
        this.documentService = documentService;
        this.ossService = ossService;
        this.knowledgeMapper = knowledgeMapper;
        this.fileMapper = fileMapper;
    }

    @Override
    public FileEntity uploadFile(
            MultipartFile file,
            Long knowledgeId
    ) {

        Long userId = UserContext.getUserId();

        log.info("开始上传文件,userId={},knowledgeId={},fileName={}",
                userId,
                knowledgeId,
                file.getOriginalFilename());


        KnowledgeEntity knowledge =
                knowledgeMapper.selectById(knowledgeId);

        if (knowledge == null) {
            throw new BusinessException("知识不存在");
        }


        if (!userId.equals(knowledge.getUserId())) {
            throw new BusinessException("无权上传");
        }

        log.info("文件权限校验通过,userId={},knowledgeId={}",
                userId,
                knowledgeId);


        String url = ossService.upload(file);

        log.info("文件上传OSS成功,fileName={}",
                file.getOriginalFilename());


        FileEntity entity = new FileEntity();

        entity.setUserId(userId);
        entity.setKnowledgeId(knowledgeId);

        entity.setFileName(file.getOriginalFilename());
        entity.setFileType(file.getContentType());
        entity.setFileSize(file.getSize());

        entity.setFileUrl(url);

        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);


        fileMapper.saveFile(entity);

        log.info("文件信息保存成功,fileId={},knowledgeId={}",
                entity.getId(),
                knowledgeId);


        documentService.handleDocument(file, entity.getId());


        log.info("文件解析切片完成,fileId={}",
                entity.getId());


        return entity;
    }
}