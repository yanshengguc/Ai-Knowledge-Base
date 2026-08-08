package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.service.EmbeddingService;
import com.yansheng.aiknowledgebase.service.IndexingService;
import com.yansheng.aiknowledgebase.service.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IndexingServiceImpl implements IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingServiceImpl.class);

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    public IndexingServiceImpl(
            EmbeddingService embeddingService,
            VectorStoreService vectorStoreService) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    // 注意：这里不加 @Transactional
    // 原因：DashVector 插入是外部网络调用，不能和 MySQL 事务绑在一起
    // 参考之前 OSS 的教训：数据库事务管不了外部资源的回滚
    @Override
    public void indexChunks(Long documentId, List<ChunkEntity> chunkList) {

        if (documentId == null) {
            throw new IllegalArgumentException("documentId不能为空");
        }

        if (chunkList == null || chunkList.isEmpty()) {
            throw new IllegalArgumentException("chunkList不能为空");
        }

        int successCount = 0;
        int failCount = 0;

        for (ChunkEntity chunk : chunkList) {

            if (chunk.getId() == null) {
                log.error("Chunk缺少id，跳过索引，content={}", chunk.getContent());
                failCount++;
                continue;
            }

            try {
                float[] vector = embeddingService.embed(chunk.getContent());

                vectorStoreService.insert(
                        chunk.getId(),
                        documentId,
                        chunk.getContent(),
                        vector
                );

                successCount++;

            } catch (Exception e) {
                // 单个chunk失败不影响其他chunk继续处理
                log.error("Chunk索引失败，chunkId={}, error={}",
                        chunk.getId(), e.getMessage(), e);
                failCount++;
            }
        }

        log.info("索引完成，documentId={}, 成功={}, 失败={}",
                documentId, successCount, failCount);
    }
}