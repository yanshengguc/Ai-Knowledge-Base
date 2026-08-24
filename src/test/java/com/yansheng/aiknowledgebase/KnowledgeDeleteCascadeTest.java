package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.common.RedisKey;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.DocumentService;
import com.yansheng.aiknowledgebase.service.VectorStoreService;
import com.yansheng.aiknowledgebase.service.impl.KnowledgeServiceImpl;
import com.yansheng.aiknowledgebase.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * P0-数据: 知识删除级联一致性(顺序 + 完整性)。
 * 出错后果:孤儿数据(删了知识还能搜到内容)或 OSS 残留对象(持续计费)。
 * InOrder 断言删除顺序:chunk → file → knowledge → vector → cache,
 * 任何一步被跳过都会红。
 */
class KnowledgeDeleteCascadeTest {

    private KnowledgeMapper knowledgeMapper;
    private FileMapper fileMapper;
    private ChunkMapper chunkMapper;
    private VectorStoreService vectorStoreService;
    private RedisTemplate<String, Object> redisTemplate;
    private KnowledgeServiceImpl knowledgeService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        knowledgeMapper = mock(KnowledgeMapper.class);
        fileMapper = mock(FileMapper.class);
        chunkMapper = mock(ChunkMapper.class);
        vectorStoreService = mock(VectorStoreService.class);
        redisTemplate = mock(RedisTemplate.class);
        knowledgeService = new KnowledgeServiceImpl(knowledgeMapper, fileMapper, chunkMapper,
                vectorStoreService, mock(DocumentService.class), redisTemplate);

        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("owner");
        UserContext.set(user);

        KnowledgeEntity knowledge = new KnowledgeEntity();
        knowledge.setId(10L);
        knowledge.setUserId(1L);
        knowledge.setAuthor("owner");
        when(knowledgeMapper.selectById(10L)).thenReturn(knowledge);
        when(knowledgeMapper.delete(10L)).thenReturn(1);

        FileEntity f1 = new FileEntity();
        f1.setId(101L);
        f1.setKnowledgeId(10L);
        FileEntity f2 = new FileEntity();
        f2.setId(102L);
        f2.setKnowledgeId(10L);
        when(fileMapper.selectFileByKnowledgeId(10L)).thenReturn(List.of(f1, f2));
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void cascadeDeletesEveryLayerInOrder() {
        knowledgeService.deleteKnowledge(10L);

        // 顺序:每个文件先删 chunk → 删文件 → 删知识 → 每个文件删向量 → 删缓存
        InOrder order = inOrder(chunkMapper, fileMapper, knowledgeMapper,
                vectorStoreService, redisTemplate);
        order.verify(chunkMapper).deleteByFileId(101L);
        order.verify(chunkMapper).deleteByFileId(102L);
        order.verify(fileMapper).deleteByKnowledgeId(10L);
        order.verify(knowledgeMapper).delete(10L);
        order.verify(vectorStoreService).deleteByFileId(101L);
        order.verify(vectorStoreService).deleteByFileId(102L);
        order.verify(redisTemplate).delete(RedisKey.knowledge(10L));

        // 完整性:无遗漏(每层恰好一次)
        verify(chunkMapper, times(1)).deleteByFileId(101L);
        verify(vectorStoreService, times(1)).deleteByFileId(102L);
    }

    @Test
    void nonExistentKnowledgeRejected() {
        when(knowledgeMapper.selectById(999L)).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> knowledgeService.deleteKnowledge(999L));
        assertEquals("不存在", e.getMessage());
        verify(chunkMapper, never()).deleteByFileId(any());
        verify(fileMapper, never()).deleteByKnowledgeId(any());
    }

    @Test
    void otherUsersKnowledgeRejectedAndNothingDeleted() {
        KnowledgeEntity others = new KnowledgeEntity();
        others.setId(20L);
        others.setUserId(999L);
        others.setAuthor("attacker");
        when(knowledgeMapper.selectById(20L)).thenReturn(others);
        when(fileMapper.selectFileByKnowledgeId(20L)).thenReturn(List.of());

        BusinessException e = assertThrows(BusinessException.class,
                () -> knowledgeService.deleteKnowledge(20L));
        assertEquals("权限不足", e.getMessage());

        // 越权删除必须被完全拦截:任何数据层都不能被碰
        verify(chunkMapper, never()).deleteByFileId(any());
        verify(fileMapper, never()).deleteByKnowledgeId(any());
        verify(knowledgeMapper, never()).delete(any());
        verify(vectorStoreService, never()).deleteByFileId(any());
        verify(redisTemplate, never()).delete(any(String.class));
    }
}
