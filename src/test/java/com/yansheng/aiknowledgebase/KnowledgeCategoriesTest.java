package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.controller.KnowledgeController;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.DocumentService;
import com.yansheng.aiknowledgebase.service.KnowledgeService;
import com.yansheng.aiknowledgebase.service.VectorStoreService;
import com.yansheng.aiknowledgebase.service.impl.KnowledgeServiceImpl;
import com.yansheng.aiknowledgebase.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B-130 分类下拉候选端点:GET /api/knowledge/categories。
 * 覆盖 ①Service 映射与空值降级 ②新端点不被 /api/knowledge/{id} 详情路由吃掉(负向对照:详情路由仍可用)。
 */
class KnowledgeCategoriesTest {

    private KnowledgeMapper knowledgeMapper;
    private KnowledgeServiceImpl knowledgeService;

    @BeforeEach
    void setUp() {
        knowledgeMapper = mock(KnowledgeMapper.class);
        knowledgeService = new KnowledgeServiceImpl(knowledgeMapper, mock(FileMapper.class),
                mock(ChunkMapper.class), mock(VectorStoreService.class), mock(DocumentService.class),
                mock(RedisTemplate.class));

        UserEntity me = new UserEntity();
        me.setId(1L);
        me.setUsername("owner");
        UserContext.set(me);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    /** 分类为自由文本,Service 原样返回 mapper 的去重结果 */
    @Test
    void categoriesReturnsDistinctListFromMapper() {
        when(knowledgeMapper.selectDistinctCategoriesByUserId(1L)).thenReturn(List.of("后端", "云原生"));

        List<String> categories = knowledgeService.getCategories();

        assertEquals(List.of("后端", "云原生"), categories);
        verify(knowledgeMapper).selectDistinctCategoriesByUserId(1L);
    }

    /** mapper 返回 null 时降级为空列表,不得抛 NPE */
    @Test
    void categoriesFallsBackToEmptyWhenMapperReturnsNull() {
        when(knowledgeMapper.selectDistinctCategoriesByUserId(1L)).thenReturn(null);

        List<String> categories = knowledgeService.getCategories();

        assertTrue(categories.isEmpty());
    }

    /** 新端点必须命中 literal 映射;若被 /knowledge/{id} 吃掉会因 "categories" 无法转 Long 而 400 */
    @Test
    void categoriesRouteIsNotShadowedByDetailRoute() throws Exception {
        KnowledgeService ks = mock(KnowledgeService.class);
        when(ks.getCategories()).thenReturn(List.of("A"));
        when(ks.getKnowledgeById(10L)).thenReturn(null);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new KnowledgeController(ks)).build();

        mvc.perform(get("/api/knowledge/categories")).andExpect(status().isOk());
        // 负向对照:详情路由仍可用
        mvc.perform(get("/api/knowledge/10")).andExpect(status().isOk());

        verify(ks).getCategories();
        verify(ks).getKnowledgeById(10L);
    }
}
