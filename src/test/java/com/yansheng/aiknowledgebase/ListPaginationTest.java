package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.controller.FileController;
import com.yansheng.aiknowledgebase.controller.KnowledgeController;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.DocumentService;
import com.yansheng.aiknowledgebase.service.FileService;
import com.yansheng.aiknowledgebase.service.KnowledgeService;
import com.yansheng.aiknowledgebase.service.OssService;
import com.yansheng.aiknowledgebase.service.RetrievalService;
import com.yansheng.aiknowledgebase.service.VectorStoreService;
import com.yansheng.aiknowledgebase.service.impl.FileServiceImpl;
import com.yansheng.aiknowledgebase.service.impl.KnowledgeServiceImpl;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.FilePageVO;
import com.yansheng.aiknowledgebase.vo.KnowledgePageVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B-104 列表服务端分页:参数护栏(size 非正 -> 默认 10、超上限 -> 50、page 下限 1)、
 * 过滤条件归一化(空白 -> null)、文件分页归属校验,以及"新增分页端点不得被既有
 * /api/knowledge/{id} 详情路由吃掉"(路由优先级回归,负向对照:旧详情路由仍可用)。
 */
class ListPaginationTest {

    private KnowledgeMapper knowledgeMapper;
    private FileMapper fileMapper;
    private KnowledgeServiceImpl knowledgeService;
    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        knowledgeMapper = mock(KnowledgeMapper.class);
        fileMapper = mock(FileMapper.class);
        knowledgeService = new KnowledgeServiceImpl(knowledgeMapper, fileMapper, mock(ChunkMapper.class),
                mock(VectorStoreService.class), mock(DocumentService.class), mock(RedisTemplate.class));
        fileService = new FileServiceImpl(mock(DocumentService.class), mock(OssService.class),
                knowledgeMapper, fileMapper, mock(ChunkMapper.class),
                Runnable::run, mock(RetrievalService.class), mock(VectorStoreService.class));

        UserEntity me = new UserEntity();
        me.setId(1L);
        me.setUsername("owner");
        UserContext.set(me);

        KnowledgeEntity mine = new KnowledgeEntity();
        mine.setId(10L);
        mine.setUserId(1L);
        mine.setAuthor("owner");
        when(knowledgeMapper.selectById(10L)).thenReturn(mine);

        KnowledgeEntity others = new KnowledgeEntity();
        others.setId(20L);
        others.setUserId(2L);
        others.setAuthor("attacker");
        when(knowledgeMapper.selectById(20L)).thenReturn(others);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void knowledgePageUsesDefaultSizeWhenSizeNotPositiveAndNormalizesBlankFilters() {
        when(knowledgeMapper.countPageByUserId(1L, null, null)).thenReturn(3L);
        KnowledgeEntity e = new KnowledgeEntity();
        e.setId(7L);
        e.setTitle("t");
        when(knowledgeMapper.selectPageByUserId(1L, null, null, 10, 0)).thenReturn(List.of(e));

        KnowledgePageVO vo = knowledgeService.getKnowledgePage(0, 0, "   ", "");

        assertEquals(1, vo.getPage());
        assertEquals(10, vo.getSize());
        assertEquals(3L, vo.getTotal());
        assertEquals(1, vo.getList().size());
        assertEquals("t", vo.getList().get(0).getTitle());
    }

    @Test
    void knowledgePageCapsSizeTo50AndPassesTrimmedFiltersWithOffset() {
        when(knowledgeMapper.countPageByUserId(1L, "k8s", "云原生")).thenReturn(120L);
        when(knowledgeMapper.selectPageByUserId(1L, "k8s", "云原生", 50, 50)).thenReturn(List.of());

        KnowledgePageVO vo = knowledgeService.getKnowledgePage(2, 999, " k8s ", " 云原生 ");

        assertEquals(2, vo.getPage());
        assertEquals(50, vo.getSize());
        assertEquals(120L, vo.getTotal());
        assertEquals(0, vo.getList().size());
    }

    @Test
    void filePageCapsSizeAndPassesOffset() {
        FileEntity f = new FileEntity();
        f.setId(101L);
        f.setFileName("my.pdf");
        when(fileMapper.countByKnowledgeId(10L)).thenReturn(60L);
        when(fileMapper.selectPageByKnowledgeId(10L, 50, 50)).thenReturn(List.of(f));

        FilePageVO vo = fileService.getFilePage(10L, 2, 999);

        assertEquals(2, vo.getPage());
        assertEquals(50, vo.getSize());
        assertEquals(60L, vo.getTotal());
        assertEquals(1, vo.getList().size());
        assertEquals("my.pdf", vo.getList().get(0).getFileName());
    }

    @Test
    void filePageDeniedForOthersKnowledge() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> fileService.getFilePage(20L, 1, 10));
        assertEquals("权限不足", e.getMessage());
    }

    /** 极端 page:offset 必须以 long 运算,否则 (page-1)*size 溢出为负 OFFSET(评审 medium) */
    @Test
    void knowledgePageExtremePageDoesNotOverflowOffset() {
        long expectedOffset = (long) (Integer.MAX_VALUE - 1) * 10;
        when(knowledgeMapper.countPageByUserId(1L, null, null)).thenReturn(1L);
        when(knowledgeMapper.selectPageByUserId(1L, null, null, 10, expectedOffset)).thenReturn(List.of());

        KnowledgePageVO vo = knowledgeService.getKnowledgePage(Integer.MAX_VALUE, 10, null, null);

        assertEquals(Integer.MAX_VALUE, vo.getPage());
        assertEquals(10, vo.getSize());
        assertTrue(vo.getList().isEmpty());
    }

    @Test
    void paginatedEndpointsResolveWithoutShadowingDetailRoute() throws Exception {
        KnowledgeService ks = mock(KnowledgeService.class);
        FileService fs = mock(FileService.class);
        when(ks.getKnowledgePage(anyInt(), anyInt(), any(), any())).thenReturn(new KnowledgePageVO());
        when(fs.getFilePage(10L, 1, 10)).thenReturn(new FilePageVO());

        FileController fileController = new FileController();
        ReflectionTestUtils.setField(fileController, "fileService", fs);

        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new KnowledgeController(ks), fileController)
                .build();

        // 新增分页端点命中分页 handler(若被 /knowledge/{id} 吃掉会因 "page" 无法转 Long 而 400)
        mvc.perform(get("/api/knowledge/page").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());
        // 负向对照:旧详情路由与文件分页路由仍可用
        mvc.perform(get("/api/knowledge/10")).andExpect(status().isOk());
        mvc.perform(get("/api/file/page/10")).andExpect(status().isOk());

        verify(ks).getKnowledgePage(2, 5, null, null);
        verify(ks).getKnowledgeById(10L);
        verify(fs).getFilePage(10L, 1, 10);
    }
}
