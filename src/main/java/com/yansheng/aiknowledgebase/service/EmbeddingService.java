package com.yansheng.aiknowledgebase.service;

import java.util.List;

public interface EmbeddingService {
    float[] embed(String text);

    /**
     * 批量向量化:一次请求处理多条文本,减少网络往返(实测比逐条循环快 5-10 倍)。
     */
    List<float[]> embedBatch(List<String> texts);
}
