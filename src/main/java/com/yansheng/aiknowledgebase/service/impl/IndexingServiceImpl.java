package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.service.EmbeddingService;
import com.yansheng.aiknowledgebase.service.IndexingService;
import com.yansheng.aiknowledgebase.service.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class IndexingServiceImpl implements IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingServiceImpl.class);

    /**
     * 每批向量化的切片数:批量接口一次请求处理一批,大幅减少网络往返。
     * (Spring AI 内部会再按模型 API 限制自动拆分,此处只是控制单次组装量)
     */
    private static final int BATCH_SIZE = 20;

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final ChunkMapper chunkMapper;

    public IndexingServiceImpl(
            EmbeddingService embeddingService,
            VectorStoreService vectorStoreService,
            ChunkMapper chunkMapper) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.chunkMapper = chunkMapper;
    }

    // 注意：这里不加 @Transactional
    // 原因：DashVector 插入是外部网络调用，不能和 MySQL 事务绑在一起
    // 参考之前 OSS 的教训：数据库事务管不了外部资源的回滚
    @Override
    public void indexChunks(Long fileId, List<ChunkEntity> chunkList) {

        if (fileId == null) {
            throw new IllegalArgumentException("fileId不能为空");
        }

        if (chunkList == null || chunkList.isEmpty()) {
            throw new IllegalArgumentException("chunkList不能为空");
        }

        int successCount = 0;
        int failCount = 0;

        // 分批处理:每批 批量向量化 + 批量入库(一次 HTTP 往返),失败回退逐条,保证单 chunk 失败不影响整体
        for (int from = 0; from < chunkList.size(); from += BATCH_SIZE) {
            List<ChunkEntity> batch = chunkList.subList(from, Math.min(from + BATCH_SIZE, chunkList.size()));

            // 跳过缺少 id 的切片(数据库未回填,无法作为向量 id)
            List<ChunkEntity> valid = new ArrayList<>();
            for (ChunkEntity chunk : batch) {
                if (chunk.getId() == null) {
                    log.error("Chunk缺少id，跳过索引，content={}", chunk.getContent());
                    failCount++;
                } else {
                    valid.add(chunk);
                }
            }
            if (valid.isEmpty()) {
                continue;
            }

            try {
                List<String> texts = valid.stream().map(ChunkEntity::getContent).toList();
                List<float[]> vectors = embeddingService.embedBatch(texts);
                vectorStoreService.insertBatch(fileId, valid, vectors);
                successCount += valid.size();
                log.info("批量索引成功，fileId={}, batchSize={}", fileId, valid.size());
            } catch (Exception e) {
                // 批量失败(如单条文本触发 API 校验) → 回退逐条索引,保持"单 chunk 失败不影响其他"语义
                log.warn("批量索引失败，回退逐条索引，fileId={}, batchSize={}, error={}",
                        fileId, valid.size(), e.getMessage());
                for (ChunkEntity chunk : valid) {
                    try {
                        float[] vector = embeddingService.embed(chunk.getContent());
                        vectorStoreService.insert(chunk.getId(), fileId, chunk.getContent(), vector);
                        successCount++;
                    } catch (Exception ex) {
                        log.error("Chunk索引失败，chunkId={}, error={}",
                                chunk.getId(), ex.getMessage(), ex);
                        failCount++;
                    }
                }
            }
        }

        log.info("索引完成，fileId={}, 成功={}, 失败={}",
                fileId, successCount, failCount);
    }

    @Override
    public void reindexFile(Long fileId) {
        if (fileId == null) {
            throw new IllegalArgumentException("fileId不能为空");
        }
        List<ChunkEntity> chunks = chunkMapper.selectByFileId(fileId);
        if (chunks == null || chunks.isEmpty()) {
            log.info("重建索引:该文件无切片, fileId={}", fileId);
            return;
        }
        log.info("重建索引开始, fileId={}, chunkCount={}", fileId, chunks.size());
        // 幂等:已存在的向量 id 会被 DashVector 跳过,重跑只补缺失向量
        indexChunks(fileId, chunks);
    }
}
