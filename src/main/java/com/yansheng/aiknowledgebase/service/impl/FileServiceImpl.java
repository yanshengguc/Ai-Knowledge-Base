package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.DocumentService;
import com.yansheng.aiknowledgebase.service.FileService;
import com.yansheng.aiknowledgebase.service.OssService;
import com.yansheng.aiknowledgebase.utils.ByteArrayMultipartFile;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.FileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

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
    public FileVO getFileById(Long id) {
        FileEntity entity = fileMapper.selectById(id);

        if (entity == null) {
            throw new BusinessException("文件不存在");
        }

        FileVO vo = new FileVO();
        vo.setId(entity.getId());
        vo.setFileName(entity.getFileName());
        vo.setFileUrl(entity.getFileUrl());
        vo.setFileSize(entity.getFileSize());
        vo.setKnowledgeId(entity.getKnowledgeId());
        vo.setUpdateTime(entity.getUpdateTime());

        return vo;
    }
    @Override
    public FileEntity uploadFile(
            MultipartFile file,
            Long knowledgeId
    ) {

        Long userId = UserContext.getUserId();
if (file.isEmpty()) {
    throw new BusinessException("文件不能为空");
}
long maxSize = 20*1024*1024;
if (file.getSize() > maxSize) {
    throw new BusinessException("文件大小不能超过20MB");
}
// 文件类型白名单(安全加固:与 ParserFactory 支持的解析类型一致,防止上传任意恶意文件到 OSS)
String originalName = file.getOriginalFilename();
if (originalName == null
        || !(originalName.toLowerCase().endsWith(".pdf")
             || originalName.toLowerCase().endsWith(".docx"))) {
    throw new BusinessException("仅支持 pdf/docx 格式文件");
}
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
entity.setStatus("PROCESSING");
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

        // 异步处理文档(解析/切片/向量化):请求立即返回,前端轮询文件状态
        // 先同步读出字节(快),避免请求结束后 MultipartFile 临时文件被清理
        final byte[] content;
        final String fileName = entity.getFileName();
        final String contentType = entity.getFileType();
        final Long fileId = entity.getId();
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("文件读取失败");
        }
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                documentService.handleDocument(
                        new ByteArrayMultipartFile(content, fileName, contentType),
                        fileId);
                fileMapper.updateStatus(fileId, "SUCCESS");
                log.info("文件处理完成,fileId={}", fileId);
            } catch (Exception e) {
                log.error("文件处理失败,fileId={}", fileId, e);
                fileMapper.updateStatus(fileId, "FAILED");
            }
        });

        return entity;
    }
}