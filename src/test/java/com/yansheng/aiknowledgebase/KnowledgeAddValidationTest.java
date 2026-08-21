package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.dto.KnowledgeAddDTO;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.VectorStoreService;
import com.yansheng.aiknowledgebase.service.impl.KnowledgeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 防脏数据回归:addKnowledge 空标题/空内容必须拒绝(防 F1 类"POST /knowledge {action:list}"写空数据)。
 */
class KnowledgeAddValidationTest {

    private KnowledgeMapper knowledgeMapper;
    private KnowledgeServiceImpl knowledgeService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        knowledgeMapper = mock(KnowledgeMapper.class);
        knowledgeService = new KnowledgeServiceImpl(
                knowledgeMapper,
                mock(FileMapper.class),
                mock(ChunkMapper.class),
                mock(VectorStoreService.class),
                mock(RedisTemplate.class));
    }

    @Test
    void emptyTitleShouldBeRejected() {
        KnowledgeAddDTO dto = new KnowledgeAddDTO();
        dto.setTitle("  ");
        dto.setContent("内容");
        assertThrows(BusinessException.class, () -> knowledgeService.addKnowledge(dto));
        verify(knowledgeMapper, never()).insert(any());
    }

    @Test
    void emptyContentShouldBeRejected() {
        KnowledgeAddDTO dto = new KnowledgeAddDTO();
        dto.setTitle("标题");
        dto.setContent(null);
        assertThrows(BusinessException.class, () -> knowledgeService.addKnowledge(dto));
        verify(knowledgeMapper, never()).insert(any());
    }

    @Test
    void nullDtoShouldBeRejected() {
        assertThrows(BusinessException.class, () -> knowledgeService.addKnowledge(null));
        verify(knowledgeMapper, never()).insert(any());
    }
}
