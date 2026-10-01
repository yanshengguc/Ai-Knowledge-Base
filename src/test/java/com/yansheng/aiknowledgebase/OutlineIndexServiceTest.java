package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.entity.OutlineChunkRefEntity;
import com.yansheng.aiknowledgebase.entity.OutlineNodeEntity;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.mapper.OutlineMapper;
import com.yansheng.aiknowledgebase.service.OssService;
import com.yansheng.aiknowledgebase.service.impl.OutlineIndexServiceImpl;
import com.yansheng.aiknowledgebase.service.splitter.StructureAwareSplitter;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.OutlineNodeDetailVO;
import com.yansheng.aiknowledgebase.vo.OutlineTreeVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B-114 Phase1 Outline 导航层:节点树 + source_chunks 关联(顺序对齐,不匹配降级)。
 * 切片由真实 StructureAwareSplitter 产出,保证"关联判据"与生产口径同源。
 */
class OutlineIndexServiceTest {

    private static final String MULTI_LEVEL_MD = """
            # 架构

            总览正文。

            ## 接入层

            接入层正文。

            ### 路由

            路由正文。

            ## 空父标题

            ### 有正文的子节

            子节正文。
            """;

    private OutlineMapper outlineMapper;
    private ChunkMapper chunkMapper;
    private FileMapper fileMapper;
    private KnowledgeMapper knowledgeMapper;
    private OssService ossService;
    private OutlineIndexServiceImpl service;

    @BeforeEach
    void setUp() {
        outlineMapper = mock(OutlineMapper.class);
        chunkMapper = mock(ChunkMapper.class);
        fileMapper = mock(FileMapper.class);
        knowledgeMapper = mock(KnowledgeMapper.class);
        ossService = mock(OssService.class);
        service = new OutlineIndexServiceImpl(outlineMapper, chunkMapper, fileMapper, knowledgeMapper, ossService);
        when(outlineMapper.insertNodes(anyList())).thenAnswer(inv -> {
            List<OutlineNodeEntity> nodes = inv.getArgument(0);
            long id = 0;
            for (OutlineNodeEntity node : nodes) {
                node.setId(++id);
            }
            return nodes.size();
        });
        when(outlineMapper.updateParents(anyList())).thenReturn(1);
        when(outlineMapper.insertRefs(anyList())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void 生成标题树_空父标题入树但不产生关联() {
        when(chunkMapper.selectByFileId(7L)).thenReturn(chunksFrom(MULTI_LEVEL_MD));

        int nodeCount = service.indexFile(7L, MULTI_LEVEL_MD);

        assertEquals(5, nodeCount, "5 个标题节点(含空父标题)");
        verify(outlineMapper).deleteNodesByFileId(7L);

        List<OutlineNodeEntity> nodes = captureInsertedNodes();
        assertEquals(List.of(1, 2, 3, 2, 3), nodes.stream().map(OutlineNodeEntity::getLevel).toList());
        assertEquals("架构 / 接入层 / 路由", nodes.get(2).getHeadingPath());
        assertEquals("架构 / 空父标题 / 有正文的子节", nodes.get(4).getHeadingPath());

        List<OutlineNodeEntity> children = captureParents();
        assertEquals(4, children.size(), "除根节点外都要回填 parent_id");
        assertEquals(nodes.get(0).getId(), nodes.get(1).getParentId());
        assertEquals(nodes.get(1).getId(), nodes.get(2).getParentId());
        assertEquals(nodes.get(3).getId(), nodes.get(4).getParentId());

        Map<Long, Integer> nodeIndexById = new HashMap<>();
        nodes.forEach(n -> nodeIndexById.put(n.getId(), n.getNodeIndex()));
        List<OutlineChunkRefEntity> refs = captureRefs();
        assertEquals(List.of(0, 1, 2, 4), refs.stream().map(r -> nodeIndexById.get(r.getNodeId())).toList(),
                "空父标题(nodeIndex=3)不产生关联");
        assertFalse(refs.stream().anyMatch(r -> nodes.get(3).getId().equals(r.getNodeId())));
    }

    @Test
    void 首行标题对不上时后续切片不再关联() {
        when(chunkMapper.selectByFileId(8L)).thenReturn(List.of(
                chunk(1L, 0, "# 架构\n总览正文。"),
                chunk(2L, 1, "## 别的标题\n别的正文。")));

        service.indexFile(8L, MULTI_LEVEL_MD);

        List<OutlineChunkRefEntity> refs = captureRefs();
        assertEquals(1, refs.size(), "只在标题行逐字相等时关联,后续整体降级");
        assertEquals(1L, refs.get(0).getChunkId().longValue(), "只保留对齐成功的第一块");
    }

    @Test
    void 同名相邻节无法区分时不误挂到前一节() {
        String markdown = "# 配置\n\n第一处配置。\n\n# 配置\n\n第二处配置。\n";
        when(chunkMapper.selectByFileId(12L)).thenReturn(chunksFrom(markdown));

        service.indexFile(12L, markdown);

        List<OutlineChunkRefEntity> refs = captureRefs();
        assertEquals(1, refs.size(), "歧义处停止关联,不把第二节误挂到第一节");
        assertEquals(1001L, refs.get(0).getChunkId().longValue());
    }

    @Test
    void 无标题前导块不归属任何节点() {
        when(chunkMapper.selectByFileId(9L)).thenReturn(List.of(
                chunk(1L, 0, "前言正文,没有标题。"),
                chunk(2L, 1, "# 架构\n总览正文。")));

        service.indexFile(9L, MULTI_LEVEL_MD);

        List<OutlineChunkRefEntity> refs = captureRefs();
        assertEquals(1, refs.size());
        assertEquals(captureInsertedNodes().get(0).getId(), refs.get(0).getNodeId());
    }

    @Test
    void 超长节被切成多块时全部归属同一节点() {
        String markdown = "# 长节\n\n" + "句子内容。".repeat(200) + "\n";
        List<ChunkEntity> chunks = chunksFrom(markdown);
        assertTrue(chunks.size() > 1, "构造前提:该节确实被打包成多块");
        when(chunkMapper.selectByFileId(10L)).thenReturn(chunks);

        service.indexFile(10L, markdown);

        List<OutlineChunkRefEntity> refs = captureRefs();
        Long nodeId = captureInsertedNodes().get(0).getId();
        assertEquals(chunks.size(), refs.size());
        assertTrue(refs.stream().allMatch(r -> nodeId.equals(r.getNodeId())));
    }

    @Test
    void 原文无标题时不写入任何节点() {
        int nodeCount = service.indexFile(11L, "纯正文,没有标题。");

        assertEquals(0, nodeCount);
        verify(outlineMapper, never()).insertNodes(anyList());
        verify(outlineMapper, never()).insertRefs(anyList());
    }

    @Test
    void 标题树返回节点与关联计数() {
        OutlineNodeEntity node = new OutlineNodeEntity();
        node.setId(1L);
        node.setFileId(7L);
        node.setNodeIndex(0);
        node.setLevel(1);
        node.setTitle("架构");
        node.setHeadingPath("架构");
        when(fileMapper.selectById(7L)).thenReturn(file(7L, 3L, "架构.md"));
        when(knowledgeMapper.selectById(3L)).thenReturn(knowledge(3L, "me"));
        when(outlineMapper.selectNodesByFileId(7L)).thenReturn(List.of(node));
        when(outlineMapper.selectRefsByFileId(7L)).thenReturn(List.of(
                ref(1L, 1001L, 0), ref(1L, 1002L, 1)));
        UserContext.set(user("me"));

        OutlineTreeVO tree = service.getTree(7L);

        assertEquals(7L, tree.getFileId().longValue());
        assertEquals("架构.md", tree.getFileName());
        assertEquals(1, tree.getNodeCount().intValue());
        assertEquals(2, tree.getNodes().get(0).getSourceChunkCount().intValue());
    }

    @Test
    void 节点详情截断溯源正文并保留定位信息() {
        OutlineNodeEntity node = new OutlineNodeEntity();
        node.setId(1L);
        node.setFileId(7L);
        node.setTitle("架构");
        when(outlineMapper.selectNodeById(1L)).thenReturn(node);
        when(fileMapper.selectById(7L)).thenReturn(file(7L, 3L, "架构.md"));
        when(knowledgeMapper.selectById(3L)).thenReturn(knowledge(3L, "me"));
        com.yansheng.aiknowledgebase.vo.OutlineSourceChunkVO source =
                new com.yansheng.aiknowledgebase.vo.OutlineSourceChunkVO();
        source.setChunkId(1001L);
        source.setChunkIndex(3);
        source.setContentLength(400);
        source.setPreview("文".repeat(400));
        when(outlineMapper.selectRefsByNodeId(1L)).thenReturn(List.of(source));
        UserContext.set(user("me"));

        OutlineNodeDetailVO detail = service.getNodeDetail(1L);

        assertEquals(1, detail.getSourceChunks().size());
        assertEquals(1001L, detail.getSourceChunks().get(0).getChunkId().longValue());
        assertEquals(3, detail.getSourceChunks().get(0).getChunkIndex().intValue());
        assertEquals(203, detail.getSourceChunks().get(0).getPreview().length());
        assertTrue(detail.getSourceChunks().get(0).getPreview().endsWith("..."));
    }

    @Test
    void 非作者访问节点详情被拒() {
        OutlineNodeEntity node = new OutlineNodeEntity();
        node.setId(1L);
        node.setFileId(7L);
        when(outlineMapper.selectNodeById(1L)).thenReturn(node);
        when(fileMapper.selectById(7L)).thenReturn(file(7L, 3L, "架构.md"));
        when(knowledgeMapper.selectById(3L)).thenReturn(knowledge(3L, "other"));
        UserContext.set(user("me"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getNodeDetail(1L));

        assertEquals("权限不足", ex.getMessage());
    }

    @Test
    void 节点不存在时报错() {
        when(outlineMapper.selectNodeById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getNodeDetail(99L));

        assertEquals("节点不存在", ex.getMessage());
    }

    @Test
    void rebuild拒绝非md文件() {
        when(fileMapper.selectById(7L)).thenReturn(file(7L, 3L, "讲义.pdf"));
        when(knowledgeMapper.selectById(3L)).thenReturn(knowledge(3L, "me"));
        UserContext.set(user("me"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.rebuild(7L));

        assertEquals("仅 md 文件支持生成标题导航层", ex.getMessage());
    }

    private List<ChunkEntity> chunksFrom(String markdown) {
        List<String> parts = new StructureAwareSplitter(500, 100).split(markdown);
        List<ChunkEntity> chunks = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            chunks.add(chunk(1001L + i, i, parts.get(i)));
        }
        return chunks;
    }

    private ChunkEntity chunk(Long id, int index, String content) {
        ChunkEntity chunk = new ChunkEntity();
        chunk.setId(id);
        chunk.setFileId(7L);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setContentLength(content.length());
        return chunk;
    }

    private OutlineChunkRefEntity ref(Long nodeId, Long chunkId, int chunkIndex) {
        OutlineChunkRefEntity ref = new OutlineChunkRefEntity();
        ref.setNodeId(nodeId);
        ref.setChunkId(chunkId);
        ref.setChunkIndex(chunkIndex);
        return ref;
    }

    private FileEntity file(Long id, Long knowledgeId, String fileName) {
        FileEntity file = new FileEntity();
        file.setId(id);
        file.setKnowledgeId(knowledgeId);
        file.setFileName(fileName);
        file.setFileUrl("https://bucket.example.com/" + fileName);
        return file;
    }

    private KnowledgeEntity knowledge(Long id, String author) {
        KnowledgeEntity knowledge = new KnowledgeEntity();
        knowledge.setId(id);
        knowledge.setAuthor(author);
        return knowledge;
    }

    private UserEntity user(String username) {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername(username);
        return user;
    }

    @SuppressWarnings("unchecked")
    private List<OutlineNodeEntity> captureInsertedNodes() {
        ArgumentCaptor<List<OutlineNodeEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(outlineMapper).insertNodes(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<OutlineNodeEntity> captureParents() {
        ArgumentCaptor<List<OutlineNodeEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(outlineMapper).updateParents(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<OutlineChunkRefEntity> captureRefs() {
        ArgumentCaptor<List<OutlineChunkRefEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(outlineMapper).insertRefs(captor.capture());
        return captor.getValue();
    }
}