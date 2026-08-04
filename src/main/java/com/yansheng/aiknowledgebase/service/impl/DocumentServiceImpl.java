package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.common.BusinessException;
import com.yansheng.aiknowledgebase.service.ChunkService;
import com.yansheng.aiknowledgebase.service.DocumentService;
import com.yansheng.aiknowledgebase.service.parser.DocumentParser;
import com.yansheng.aiknowledgebase.service.parser.ParserFactory;
import com.yansheng.aiknowledgebase.service.splitter.DocumentSplitter;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class DocumentServiceImpl implements DocumentService {
    private final ParserFactory parserFactory;
    private final DocumentSplitter documentSplitter;
    private final ChunkService chunkService;

    public DocumentServiceImpl(ParserFactory parserFactory, DocumentSplitter documentSplitter, ChunkService chunkService) {
        this.parserFactory = parserFactory;
        this.documentSplitter = documentSplitter;
        this.chunkService = chunkService;
    }

    @Override
    public void handleDocument(MultipartFile file, Long fileId) {
        DocumentParser parser = parserFactory.getParser(file);
        String text = parser.parse(file);

        if (text == null || text.isBlank()) {
            throw new BusinessException("文档内容为空");
        }


        List<String> chunks = documentSplitter.split(text);

        chunkService.saveChunks(fileId, chunks);
    }

}