package com.yansheng.aiknowledgebase;


import com.yansheng.aiknowledgebase.common.RedisKey;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.DocumentService;
import com.yansheng.aiknowledgebase.service.VectorStoreService;
import com.yansheng.aiknowledgebase.service.impl.KnowledgeServiceImpl;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.KnowledgeDetailVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 缓存锁等待(重试)分支回归:抢锁失败的线程从缓存读到数据时,
 * 必须同样经过 NULL 哨兵判断与归属校验。
 * 修复前该分支直接 (KnowledgeDetailVO) obj 返回:
 *  - 读到他人缓存 → 越权返回他人知识
 *  - 读到 "NULL" 哨兵 → ClassCastException
 */
class KnowledgeCacheLockRetryTest {

    private static final Long KB_ID = 42L;

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
    private final KnowledgeServiceImpl knowledgeService = new KnowledgeServiceImpl(
            mock(KnowledgeMapper.class), mock(FileMapper.class), mock(ChunkMapper.class),
            mock(VectorStoreService.class), mock(DocumentService.class), redisTemplate);

    private UserEntity userA;
    private UserEntity userB;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 抢锁失败:走"等待锁"重试分支
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                .thenReturn(false);
        userA = new UserEntity();
        userA.setId(1L);
        userA.setUsername("lock-user-A");
        userB = new UserEntity();
        userB.setId(2L);
        userB.setUsername("lock-user-B");
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private KnowledgeDetailVO voOf(String author) {
        KnowledgeDetailVO vo = new KnowledgeDetailVO();
        vo.setId(KB_ID);
        vo.setTitle("t");
        vo.setContent("c");
        vo.setAuthor(author);
        return vo;
    }

    @Test
    void lockRetryMustRejectOthersCachedKnowledge() throws Exception {
        // 首读未命中 → 等锁重试读到 B 的缓存 VO:A 访问必须被拒(修复前直接返回 B 的数据)
        when(valueOperations.get(RedisKey.knowledge(KB_ID)))
                .thenReturn(null, voOf(userB.getUsername()));

        UserContext.set(userA);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> knowledgeService.getKnowledgeById(KB_ID));
        assertEquals("权限不足", ex.getMessage());
    }

    @Test
    void lockRetryMustHonorNullSentinel() throws Exception {
        // 重试读到 "NULL" 哨兵:应报"不存在"(修复前是 ClassCastException)
        when(valueOperations.get(RedisKey.knowledge(KB_ID)))
                .thenReturn(null, "NULL");

        UserContext.set(userA);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> knowledgeService.getKnowledgeById(KB_ID));
        assertEquals("不存在", ex.getMessage());
    }

    @Test
    void lockRetryReturnsOwnCachedKnowledge() throws Exception {
        // 重试读到自己的缓存 VO:正常返回
        when(valueOperations.get(RedisKey.knowledge(KB_ID)))
                .thenReturn(null, voOf(userA.getUsername()));

        UserContext.set(userA);
        KnowledgeDetailVO vo = knowledgeService.getKnowledgeById(KB_ID);
        assertEquals(userA.getUsername(), vo.getAuthor());
    }
}
