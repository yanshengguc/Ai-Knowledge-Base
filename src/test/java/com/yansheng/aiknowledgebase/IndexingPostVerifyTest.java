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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * B-122 索引后校验护栏回归:
 * 索引完成后按 chunk id 回查向量实际落库数——不符只告警,
 * 不抛异常、不阻断索引结果与主流程。
 */
class IndexingPostVerifyTest {

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

    private ChunkEntity chunk(Long id, String funcBody) {
        ChunkEntity c = new ChunkEntity();
        c.setId(id);
        c.setContent(funcBody);
        return c;
    }

    @Test
    void verifyShouldQueryByAllIndexedChunkIds() {
        when(chunkMapper.selectByFileId(7L)).thenReturn(List.of(chunk(11L, "a"), chunk(12L, "b")));
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(new float[]{1f}, new float[]{2f}));
        when(vectorStoreService.countExisting(anyList())).thenReturn(2);

        indexingService.reindexFile(7L);

        verify(vectorStoreService).countExisting(
                argThat(ids -> ids.size() == 2 && ids.contains(11L) && ids.contains(12L)));
    }

    @Test
    void verifyShouldNotThrowWhenVectorCountMismatch() {
        when(chunkMapper.selectByFileId(7L)).thenReturn(List.of(chunk(11L, "a"), chunk(12L, "b")));
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(new float[]{1f}, new float[]{2f}));
        // 期望 2 条,实际只回查到 1 条 -> 护栏只告警,不影响索引结果
        when(vectorStoreService.countExisting(anyList())).thenReturn(1);

        assertDoesNotThrow(() -> indexingService.reindexFile(7L));
        verify(vectorStoreService).insertBatch(eq(7L), anyList(), anyList());
    }

    @Test
    void verifyShouldSwallowItsOwnFailure() {
        when(chunkMapper.selectByFileId(7L)).thenReturn(List.of(chunk(11L, "a")));
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(new float[]{1f}));
        // 护栏自身抛异常(如向量库不可用)不得影响索引结果
        when(vectorStoreService.countExisting(anyList())).thenThrow(new RuntimeException("向量库不可用"));

        assertDoesNotThrow(() -> indexingService.reindexFile(7L));
        verify(vectorStoreService).insertBatch(eq(7L), anyList(), anyList());
    }
}
