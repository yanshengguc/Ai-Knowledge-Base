package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.entity.ChunkEntity;

import java.util.List;

public interface IndexingService {
    void indexChunks(Long fileId, List<ChunkEntity> chunkList);

    /**
     * 重建某个文件的向量索引(失败补偿入口)。
     * 幂等:DashVector 对已存在 id 的插入会跳过,重跑只会补上缺失的向量,不会重复/覆盖。
     */
    void reindexFile(Long fileId);
}