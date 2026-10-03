package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.entity.SearchResult;

import java.util.List;

public interface VectorStoreService {
    void insert(Long chunkId, Long fileId, String content, float[] vector);

    /**
     * 批量插入:一次请求写入多条向量,与 {@link #insert} 语义一致。
     * chunks 与 vectors 按下标一一对应。
     */
    void insertBatch(Long fileId, List<ChunkEntity> chunks, List<float[]> vectors);

    /**
     * 回查给定 chunk id 在向量库中实际存在的数量(B-122 索引后校验护栏,只读不回写)。
     * 索引完成后用它与实际写入的切片数比对,发现"日志报成功但向量缺失"。
     * 向量库不可用时抛 IllegalStateException,由调用方降级为告警。
     */
    int countExisting(List<Long> chunkIds);

    List<SearchResult> search(float[] vector,int topK);

    /**
     * 带条件检索:DashVector filter 表达式(如 "file_id = 1 OR file_id = 2"),
     * 用于按用户/文件范围隔离检索结果。
     */
    List<SearchResult> search(float[] vector, int topK, String filter);

    /**
     * 按文件删除向量(级联删除/同名覆盖时调用,防止"删了还能搜到"的数据残留)。
     */
    void deleteByFileId(Long fileId);
}
