package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.service.EmbeddingService;
import com.yansheng.aiknowledgebase.service.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
public class VectorStoreTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Test
    public void testInsert() {
        String text = "这是一段用于测试向量存储的文本";
        float[] vector = embeddingService.embed(text);

        vectorStoreService.insert(1L, 1001L, text, vector);

        System.out.println("插入完成,向量维度: " + vector.length);
    }
}