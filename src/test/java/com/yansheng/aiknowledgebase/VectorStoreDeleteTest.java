package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.service.EmbeddingService;
import com.yansheng.aiknowledgebase.service.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 向量删除回归(防"删了还能搜到"的数据残留):
 * 用专用测试 fileId(9999991)避免污染真实数据——插入→删除→按该文件过滤检索必须为空。
 */
@SpringBootTest
@ActiveProfiles("local")
class VectorStoreDeleteTest {

    /** 专用测试 fileId,真实数据不会用到 */
    private static final long TEST_FILE_ID = 9999991L;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Test
    void deleteByFileIdShouldRemoveVectors() {
        // 先清理可能残留的旧测试数据
        vectorStoreService.deleteByFileId(TEST_FILE_ID);

        // 插入两条该文件的向量
        List<String> texts = List.of("向量删除测试内容一", "向量删除测试内容二");
        List<float[]> vectors = embeddingService.embedBatch(texts);
        ChunkEntity c1 = new ChunkEntity();
        c1.setId(9000001L);
        c1.setContent(texts.get(0));
        ChunkEntity c2 = new ChunkEntity();
        c2.setId(9000002L);
        c2.setContent(texts.get(1));
        vectorStoreService.insertBatch(TEST_FILE_ID, List.of(c1, c2), vectors);

        // 插入后:按该文件过滤检索应有结果
        List<SearchResult> before = vectorStoreService.search(vectors.get(0), 5, "file_id = " + TEST_FILE_ID);
        assertFalse(before.isEmpty(), "删除前应能检索到该文件向量");

        // 按文件删除
        vectorStoreService.deleteByFileId(TEST_FILE_ID);

        // 删除后:轮询等待向量消失(DashVector 删除为最终一致,最多 15s)
        boolean gone = false;
        for (int i = 0; i < 15; i++) {
            if (vectorStoreService.search(vectors.get(0), 5, "file_id = " + TEST_FILE_ID).isEmpty()) {
                gone = true;
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertTrue(gone, "删除后不应再检索到该文件向量(数据残留=bug)");
    }
}
