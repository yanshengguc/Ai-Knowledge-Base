package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.EmbeddingService;
import com.yansheng.aiknowledgebase.service.impl.GraphServiceImpl;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.GraphVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 知识图谱单元测试(纯 Mockito,不起 Spring 不连外部服务)。
 * 验证:节点/结构边组装、相似边 top-K+阈值+去重、空库、embedding 失败降级、未登录拒绝。
 * 向量用手写低维数据验证 cosine 数学(维度无关),不依赖真实 embedding 服务。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GraphServiceTest {

    @Mock
    private KnowledgeMapper knowledgeMapper;
    @Mock
    private FileMapper fileMapper;
    @Mock
    private ChunkMapper chunkMapper;
    @Mock
    private EmbeddingService embeddingService;

    private GraphServiceImpl graphService;

    @BeforeEach
    void setUp() {
        graphService = new GraphServiceImpl(knowledgeMapper, fileMapper, chunkMapper, embeddingService);
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("u1");
        UserContext.set(user);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private KnowledgeEntity knowledge(long id, String title) {
        KnowledgeEntity k = new KnowledgeEntity();
        k.setId(id);
        k.setTitle(title);
        k.setUserId(1L);
        k.setAuthor("u1");
        return k;
    }

    private FileEntity file(long id, long knowledgeId, String name) {
        FileEntity f = new FileEntity();
        f.setId(id);
        f.setKnowledgeId(knowledgeId);
        f.setFileName(name);
        f.setUserId(1L);
        f.setStatus("SUCCESS");
        f.setFileType("text/markdown");
        return f;
    }

    private ChunkEntity chunk(long fileId, int index, String content) {
        ChunkEntity c = new ChunkEntity();
        c.setFileId(fileId);
        c.setChunkIndex(index);
        c.setContent(content);
        return c;
    }

    @Test
    void testNodesAndStructureEdges() {
        when(knowledgeMapper.selectByUserId(1L)).thenReturn(List.of(
                knowledge(10, "盐集架构"), knowledge(20, "设计模式")));
        when(fileMapper.selectFileByKnowledgeId(10L)).thenReturn(List.of(
                file(101, 10, "HANDOFF.md"), file(102, 10, "部署笔记")));
        when(fileMapper.selectFileByKnowledgeId(20L)).thenReturn(List.of(
                file(201, 20, "单例模式.md")));
        // embedding 失败也不影响结构部分(本用例只验证结构边,顺便验证降级)
        when(embeddingService.embedBatch(anyList())).thenThrow(new RuntimeException("mock down"));

        GraphVO vo = graphService.getGraph();

        assertEquals(5, vo.getNodes().size(), "2 条目 + 3 文件 = 5 节点");
        assertEquals(3, vo.getEdges().size(), "3 条结构边");
        assertTrue(vo.getNodes().stream().anyMatch(n -> "k-10".equals(n.getId()) && "knowledge".equals(n.getType())));
        assertTrue(vo.getNodes().stream().anyMatch(n -> "f-201".equals(n.getId()) && "file".equals(n.getType())));
        assertTrue(vo.getEdges().stream().allMatch(e ->
                "structure".equals(e.getType()) && e.getSource().startsWith("k-") && e.getTarget().startsWith("f-")));
        // 降级:embedding 挂了仍有结构边(图可渲染)
        assertTrue(vo.getEdges().stream().noneMatch(e -> "similar".equals(e.getType())));
    }

    @Test
    void testSimilarEdges_topK_threshold_andDedup() {
        // 4 个文件在一个条目下,手写 2 维向量(余弦与维度无关):
        // A=[1,0] B=[0.8,0.6] C=[0.6,0.8] E=[-1,0]
        // cos(A,B)=0.8  cos(A,C)=0.6  cos(B,C)=0.96  cos(E,*) 全为负或零
        when(knowledgeMapper.selectByUserId(1L)).thenReturn(List.of(knowledge(10, "K")));
        when(fileMapper.selectFileByKnowledgeId(10L)).thenReturn(List.of(
                file(1, 10, "A"), file(2, 10, "B"), file(3, 10, "C"), file(4, 10, "E")));
        when(chunkMapper.selectByFileId(anyLong())).thenReturn(List.of());
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(
                new float[]{1f, 0f},
                new float[]{0.8f, 0.6f},
                new float[]{0.6f, 0.8f},
                new float[]{-1f, 0f}));

        GraphVO vo = graphService.getGraph();

        List<GraphVO.EdgeVO> similar = vo.getEdges().stream()
                .filter(e -> "similar".equals(e.getType())).toList();
        // A top2: B(0.8), C(0.6) → A-B, A-C;B top2: C(0.96), A(0.8) → B-C 新增,B-A 去重;
        // C top2: B(0.96), A(0.6) → 全重复;E 全部低于阈值 → 不建边
        assertEquals(3, similar.size(), "A-B/A-C/B-C 三条相似边,E 被阈值过滤");
        Set<String> pairs = new HashSet<>();
        for (GraphVO.EdgeVO e : similar) {
            pairs.add(pairKey(e.getSource(), e.getTarget()));
        }
        assertTrue(pairs.contains("f-1|f-2"), "A-B 边存在");
        assertTrue(pairs.contains("f-1|f-3"), "A-C 边存在");
        assertTrue(pairs.contains("f-2|f-3"), "B-C 边存在");
        assertTrue(pairs.stream().noneMatch(p -> p.contains("f-4")), "E 不与任何文件建边");
        // weight = 余弦相似度
        GraphVO.EdgeVO ab = similar.stream()
                .filter(e -> pairKey(e.getSource(), e.getTarget()).equals("f-1|f-2")).findFirst().orElseThrow();
        assertEquals(0.8, ab.getWeight(), 1e-6, "A-B 权重应为余弦 0.8");
        // 同一条边 source/target 顺序稳定(小 id 在前),前端高亮邻居逻辑不依赖边方向
        assertEquals("f-1", ab.getSource());
        assertEquals("f-2", ab.getTarget());
    }

    private String pairKey(String a, String b) {
        return String.join("|", a.compareTo(b) <= 0 ? List.of(a, b) : List.of(b, a));
    }

    @Test
    void testEmptyGraph() {
        when(knowledgeMapper.selectByUserId(1L)).thenReturn(List.of());
        GraphVO vo = graphService.getGraph();
        assertNotNull(vo.getNodes());
        assertNotNull(vo.getEdges());
        assertTrue(vo.getNodes().isEmpty(), "无条目 → 空节点");
        assertTrue(vo.getEdges().isEmpty(), "无条目 → 空边");
    }

    @Test
    void testEmbedText_usesFileNamePlusFirstChunk() {
        // 验证参与 embedding 的文本 = 文件名 + 首切片(按 chunkIndex 取最早),且截断到 500
        when(knowledgeMapper.selectByUserId(1L)).thenReturn(List.of(knowledge(10, "K")));
        when(fileMapper.selectFileByKnowledgeId(10L)).thenReturn(List.of(file(1, 10, "我的笔记")));
        when(chunkMapper.selectByFileId(1L)).thenReturn(List.of(
                chunk(1, 2, "后面的切片"),
                chunk(1, 0, "首切片内容")));
        List<float[]> captured = List.of(new float[]{1f, 0f}, new float[]{0f, 1f});
        when(embeddingService.embedBatch(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            assertEquals(1, texts.size());
            assertTrue(texts.get(0).startsWith("我的笔记\n首切片内容"),
                    "embedding 文本应以文件名+首切片开头,实际: " + texts.get(0));
            assertFalse(texts.get(0).contains("后面的切片"), "只取首切片,不拼全部切片");
            return captured;
        });

        GraphVO vo = graphService.getGraph();
        assertEquals(0, vo.getEdges().stream().filter(e -> "similar".equals(e.getType())).count(),
                "单文件无相似边");
    }

    @Test
    void testEmbedTextFallsBack_whenNoChunk() {
        // 无切片(如 FAILED 文件):文本退化为文件名,仍可 embedding,不抛异常
        when(knowledgeMapper.selectByUserId(1L)).thenReturn(List.of(knowledge(10, "K")));
        when(fileMapper.selectFileByKnowledgeId(10L)).thenReturn(List.of(
                file(1, 10, "A"), file(2, 10, "B")));
        when(chunkMapper.selectByFileId(1L)).thenReturn(List.of());
        when(chunkMapper.selectByFileId(2L)).thenReturn(List.of());
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(
                new float[]{1f, 0f}, new float[]{1f, 0f}));

        GraphVO vo = graphService.getGraph();
        // cos(A,B)=1 → 有一条相似边
        assertEquals(1, vo.getEdges().stream().filter(e -> "similar".equals(e.getType())).count());
    }

    @Test
    void testEmbedBatching_overProviderLimit() {
        // 45 个文件 > text-embedding-v3 实测上限 10 → 必须分批(8×5+5),拼接结果后相似边仍可生成
        List<FileEntity> files = new java.util.ArrayList<>();
        for (long i = 1; i <= 45; i++) {
            files.add(file(i, 10, "F" + i));
        }
        when(knowledgeMapper.selectByUserId(1L)).thenReturn(List.of(knowledge(10, "K")));
        when(fileMapper.selectFileByKnowledgeId(10L)).thenReturn(files);
        when(chunkMapper.selectByFileId(anyLong())).thenReturn(List.of());
        // 每批返回与请求等量的同向向量 → 两两 cos=1
        when(embeddingService.embedBatch(anyList())).thenAnswer(inv -> {
            List<String> batch = inv.getArgument(0);
            return batch.stream().map(t -> new float[]{1f, 0f}).toList();
        });

        GraphVO vo = graphService.getGraph();

        var embedCalls = org.mockito.Mockito.mockingDetails(embeddingService).getInvocations().stream()
                .filter(inv -> "embedBatch".equals(inv.getMethod().getName())).toList();
        assertEquals(6, embedCalls.size(), "45 条应分 6 批(8×5+5)");
        var batchSizes = embedCalls.stream()
                .map(inv -> ((List<?>) inv.getArgument(0)).size()).toList();
        assertEquals(List.of(8, 8, 8, 8, 8, 5), batchSizes, "分批大小应为 8×5+5(算法层实测上限 10,留余量)");
        long similarCount = vo.getEdges().stream().filter(e -> "similar".equals(e.getType())).count();
        assertTrue(similarCount > 0, "分批拼接后相似边仍应生成,实际: " + similarCount);
    }

    @Test
    void testNotLoggedIn_rejected() {
        UserContext.remove();
        assertThrows(BusinessException.class, () -> graphService.getGraph(), "未登录应拒绝");
    }

    @Test
    void testCosineMath() {
        assertEquals(1.0, GraphServiceImpl.cosine(new float[]{1, 0}, new float[]{2, 0}), 1e-6, "同向 = 1");
        assertEquals(0.0, GraphServiceImpl.cosine(new float[]{1, 0}, new float[]{0, 1}), 1e-6, "正交 = 0");
        assertEquals(-1.0, GraphServiceImpl.cosine(new float[]{1, 0}, new float[]{-1, 0}), 1e-6, "反向 = -1");
        assertEquals(0.0, GraphServiceImpl.cosine(new float[]{0, 0}, new float[]{1, 0}), 1e-6, "零向量 = 0");
        assertEquals(0.0, GraphServiceImpl.cosine(null, new float[]{1, 0}), 1e-6, "null 保护");
    }
}
