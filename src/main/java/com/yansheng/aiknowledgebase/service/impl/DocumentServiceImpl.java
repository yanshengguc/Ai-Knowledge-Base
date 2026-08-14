package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.service.ChunkService;
import com.yansheng.aiknowledgebase.service.DocumentService;
import com.yansheng.aiknowledgebase.service.IndexingService;
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

    public DocumentServiceImpl(ParserFactory parserFactory,
                               DocumentSplitter documentSplitter,
                               ChunkService chunkService,
                               IndexingService indexingService) {
        this.parserFactory = parserFactory;
        this.documentSplitter = documentSplitter;
        this.chunkService = chunkService;
        this.indexingService = indexingService;
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
    }
}