package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
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
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileServiceImpl implements FileService {

    private final DocumentService documentService;
    private final OssService ossService;
    private final KnowledgeMapper knowledgeMapper;
    private final FileMapper fileMapper;
    private final ChunkMapper chunkMapper;

    public FileServiceImpl(DocumentService documentService,
                           OssService ossService,
                           KnowledgeMapper knowledgeMapper,
                           FileMapper fileMapper,
                           ChunkMapper chunkMapper) {
        this.documentService = documentService;
        this.ossService = ossService;
        this.knowledgeMapper = knowledgeMapper;
        this.fileMapper = fileMapper;
        this.chunkMapper = chunkMapper;
    }

    @Override
    public FileVO getFileById(Long id) {
        FileEntity entity = fileMapper.selectById(id);

        if (entity == null) {
            throw new BusinessException("文件不存在");
        }

        return toVO(entity);
    }

    private FileVO toVO(FileEntity entity) {
        FileVO vo = new FileVO();
        vo.setId(entity.getId());
        vo.setFileName(entity.getFileName());
        vo.setFileUrl(entity.getFileUrl());
        vo.setFileSize(entity.getFileSize());
        vo.setKnowledgeId(entity.getKnowledgeId());
        vo.setStatus(entity.getStatus());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    @Override
    public List<FileVO> listByKnowledgeId(Long knowledgeId) {
        return fileMapper.selectFileByKnowledgeId(knowledgeId).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
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

        // ===== 版本管理:同名文件重新上传 = 覆盖更新(删旧切片+旧记录+OSS 对象,再入库新版本) =====
        // 面试讲法:知识库更新/增量 —— 同名覆盖保证库里永远是最新版,不残留过期切片
        List<FileEntity> existing = fileMapper.selectFileByKnowledgeId(knowledgeId);
        String newFileName = file.getOriginalFilename();
        for (FileEntity old : existing) {
            if (old.getFileName() != null && old.getFileName().equals(newFileName)) {
                log.info("检测到同名文件,执行覆盖更新: oldFileId={}, fileName={}", old.getId(), newFileName);
                // 级联删除旧版本(切片 → 记录 → OSS 对象)
                chunkMapper.deleteByFileId(old.getId());
                fileMapper.deleteById(old.getId());
                ossService.delete(old.getFileUrl());
                log.info("旧版本已清理,fileId={}", old.getId());
            }
        }

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

    @Override
    public void deleteFile(Long id) {
        FileEntity file = fileMapper.selectById(id);
        if (file == null) {
            throw new BusinessException("文件不存在");
        }
        // 权限校验:文件所属知识必须是当前用户的
        KnowledgeEntity knowledge = knowledgeMapper.selectById(file.getKnowledgeId());
        com.yansheng.aiknowledgebase.entity.UserEntity user = UserContext.get();
        if (knowledge == null || user == null || !user.getUsername().equals(knowledge.getAuthor())) {
            throw new BusinessException("无权删除该文件");
        }
        // 级联:先删切片 → 再删记录 → 最后删 OSS 对象(失败降级)
        chunkMapper.deleteByFileId(id);
        fileMapper.deleteById(id);
        ossService.delete(file.getFileUrl());
        log.info("文件已删除,fileId={},knowledgeId={}", id, file.getKnowledgeId());
    }
}