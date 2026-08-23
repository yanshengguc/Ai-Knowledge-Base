package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.service.EmbeddingService;
import com.yansheng.aiknowledgebase.service.VectorStoreService;
import com.yansheng.aiknowledgebase.service.impl.IndexingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * B1 补偿回归:reindexFile 重建某文件向量索引,走批量路径,空切片不空跑。
 */
class IndexingReindexTest {

    private EmbeddingService embeddingService;
    private VectorStoreService vectorStoreService;
    private ChunkMapper chunkMapper;
    private IndexingServiceImpl indexingService;

    @BeforeEach
    void setUp() {
        embeddingService = mock(EmbeddingService.class);
        vectorStoreService = mock(VectorStoreService.class);
        chunkMapper = mock(ChunkMapper.class);
        indexingService = new IndexingServiceImpl(embeddingService, vectorStoreService, chunkMapper, 20);
    }

    private ChunkEntity chunk(Long id, String content) {
        ChunkEntity c = new ChunkEntity();
        c.setId(id);
        c.setContent(content);
        return c;
    }

    @Test
    void reindexShouldRebuildMissingVectorsInBatch() {
        when(chunkMapper.selectByFileId(7L)).thenReturn(
                List.of(chunk(11L, "内容一"), chunk(12L, "内容二")));
        when(embeddingService.embedBatch(anyList())).thenReturn(
                List.of(new float[]{1f, 2f}, new float[]{3f, 4f}));

        indexingService.reindexFile(7L);

        verify(embeddingService).embedBatch(anyList());
        verify(vectorStoreService).insertBatch(eq(7L), anyList(), anyList());
    }

    @Test
    void reindexWithNoChunksShouldDoNothing() {
        when(chunkMapper.selectByFileId(7L)).thenReturn(List.of());

        assertDoesNotThrow(() -> indexingService.reindexFile(7L));

        verify(embeddingService, never()).embedBatch(anyList());
        verify(vectorStoreService, never()).insertBatch(any(), anyList(), anyList());
    }
}
