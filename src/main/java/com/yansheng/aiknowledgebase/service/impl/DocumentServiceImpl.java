package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.service.ChunkService;
import com.yansheng.aiknowledgebase.service.DocumentService;
import com.yansheng.aiknowledgebase.service.IndexingService;
import com.yansheng.aiknowledgebase.service.OutlineIndexService;
import com.yansheng.aiknowledgebase.service.parser.DocumentParser;
import com.yansheng.aiknowledgebase.service.parser.ParserFactory;
import com.yansheng.aiknowledgebase.service.splitter.DocumentSplitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    private final ParserFactory parserFactory;
    private final DocumentSplitter documentSplitter;
    private final ChunkService chunkService;
    private final IndexingService indexingService;
    /** B-114 Outline 导航层(仅 md;失败降级不影响主链路) */
    private final OutlineIndexService outlineIndexService;

    public DocumentServiceImpl(ParserFactory parserFactory,
                               DocumentSplitter documentSplitter,
                               ChunkService chunkService,
                               IndexingService indexingService,
                               OutlineIndexService outlineIndexService) {
        this.parserFactory = parserFactory;
        this.documentSplitter = documentSplitter;
        this.chunkService = chunkService;
        this.indexingService = indexingService;
        this.outlineIndexService = outlineIndexService;
    }


    @Override
    public void handleDocument(MultipartFile file, Long fileId) {

        log.info("开始处理文档,fileId={}", fileId);

        DocumentParser parser = parserFactory.getParser(file);

        String text = parser.parse(file);




        if (text == null || text.isBlank()) {
            throw new BusinessException("文档内容为空");
        }
        log.info("文档解析完成,fileId={},textLength={}",
                fileId,
                text.length());

        List<String> chunks = documentSplitter.split(text);

        log.info("文档切片完成,fileId={},chunkCount={}",
                fileId,
                chunks.size());


        List<ChunkEntity> chunkEntities = chunkService.saveChunks(fileId, chunks);

        log.info("Chunk保存完成,fileId={},chunkCount={}",
                fileId,
                chunks.size());

        // 向量化入库:切片 → Embedding → DashVector
        // 说明:indexChunks 内部不加事务(外部网络调用),单 chunk 失败已 catch;
        // 整体失败会向上抛,由 FileServiceImpl 置文件状态 FAILED(数据不完整,语义正确)
        indexingService.indexChunks(fileId, chunkEntities);

        log.info("向量化入库完成,fileId={},chunkCount={}",
                fileId,
                chunkEntities.size());

        // B-114 导航层:主链路成功后追加,失败只降级(不改切片、不改向量、不改检索)
        indexOutlineIfMarkdown(file, fileId, text);
    }

    @Override
    public void indexPlainText(Long fileId, String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException("笔记内容为空");
        }
        log.info("开始索引纯文本笔记,fileId={},textLength={}", fileId, text.length());

        List<String> chunks = documentSplitter.split(text);
        List<ChunkEntity> chunkEntities = chunkService.saveChunks(fileId, chunks);
        indexingService.indexChunks(fileId, chunkEntities);

        log.info("笔记索引完成,fileId={},chunkCount={}", fileId, chunkEntities.size());
    }

    /**
     * B-114 Outline 导航层:仅 md 文件(标题层级是树的前提)。
     * 失败只打 WARN:导航层不是主链路,新环境漏建表时文件仍应处理成功。
     */
    private void indexOutlineIfMarkdown(MultipartFile file, Long fileId, String text) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".md")) {
            return;
        }
        try {
            int nodeCount = outlineIndexService.indexFile(fileId, text);
            log.info("Outline 导航层完成,fileId={},nodeCount={}", fileId, nodeCount);
        } catch (Exception e) {
            log.warn("Outline 导航层生成失败,fileId={},不影响切片与检索主链路", fileId, e);
        }
    }
}