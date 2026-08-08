package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.common.SearchResult;
import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.service.ChunkService;
import com.yansheng.aiknowledgebase.service.IndexingService;
import com.yansheng.aiknowledgebase.service.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class ChunkIndexingIntegrationTest {

    @Autowired
    private ChunkService chunkService;

    @Autowired
    private IndexingService indexingService;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Test
    void testSaveChunksAndIndex() {

        Long fileId = 2L;
        List<String> chunks = List.of(
                "这是第一段测试内容，关于用户认证模块的实现。",
                "这是第二段测试内容，关于向量检索的原理。"
        );

        List<ChunkEntity> savedChunks = chunkService.saveChunks(fileId, chunks);

        assertNotNull(savedChunks);
        assertEquals(2, savedChunks.size());
        savedChunks.forEach(c -> {
            assertNotNull(c.getId(), "chunkId不应为null，说明回填失败");
            System.out.println("chunkId=" + c.getId());
        });

        assertDoesNotThrow(() -> indexingService.indexChunks(fileId, savedChunks));
    }

    @Test
    void testSearch() {
        List<SearchResult> results = vectorSearchService.search("用户认证是怎么实现的", 3);

        assertNotNull(results);
        assertFalse(results.isEmpty(), "搜索结果不应为空");

        results.forEach(r -> System.out.println(
                "chunkId=" + r.getChunkId()
                        + ", documentId=" + r.getDocumentId()
                        + ", content=" + r.getContent()
                        + ", score=" + r.getScore()
        ));
    }
}