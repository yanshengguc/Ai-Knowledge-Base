package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.service.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class VectorSearchServiceTest {

    @Autowired
    private VectorSearchService vectorSearchService;

    @Test
    void testSearch() {

        // 1. 模拟用户问题
        String query = "Redis缓存击穿怎么解决？";

        // 2. 查询 Top-K
        int topK = 5;

        List<SearchResult> results =
                vectorSearchService.search(query, topK);

        // 3. 打印检索结果
        System.out.println("========== 向量检索结果 ==========");

        for (SearchResult result : results) {
            System.out.println("chunkId = " + result.getChunkId());
            System.out.println("documentId = " + result.getDocumentId());
            System.out.println("score = " + result.getScore());
            System.out.println("content = " + result.getContent());
            System.out.println("--------------------------------");
        }

        // 4. 基础断言
        assert results != null;
        assert results.size() <= topK;
    }
}