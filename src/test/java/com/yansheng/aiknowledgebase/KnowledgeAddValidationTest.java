package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.dto.KnowledgeAddDTO;
import com.yansheng.aiknowledgebase.entity.ChunkEntity;
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
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * 防脏数据回归:addKnowledge 空标题/空内容必须拒绝(防 F1 类"POST /knowledge {action:list}"写空数据)。
 * createNote:校验 + 归属 + 同步索引(写优先)。
 */
class KnowledgeAddValidationTest {

    private KnowledgeMapper knowledgeMapper;
    private FileMapper fileMapper;
    private DocumentService documentService;
    private ChunkMapper chunkMapper;
    private KnowledgeServiceImpl knowledgeService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        knowledgeMapper = mock(KnowledgeMapper.class);
        fileMapper = mock(FileMapper.class);
        documentService = mock(DocumentService.class);
        chunkMapper = mock(ChunkMapper.class);
        knowledgeService = new KnowledgeServiceImpl(
                knowledgeMapper,
                fileMapper,
                chunkMapper,
                mock(VectorStoreService.class),
                documentService,
                mock(RedisTemplate.class));
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
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

    @Test
    void noteEmptyTitleOrContentShouldBeRejected() {
        assertThrows(BusinessException.class, () -> knowledgeService.createNote(1L, " ", "内容", null));
        assertThrows(BusinessException.class, () -> knowledgeService.createNote(1L, "标题", null, null));
        verify(documentService, never()).indexPlainText(any(), any());
    }

    @Test
    void noteShouldCheckOwnershipAndIndexContent() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("tester");
        UserContext.set(user);

        KnowledgeEntity knowledge = new KnowledgeEntity();
        knowledge.setId(10L);
        knowledge.setUserId(1L);
        when(knowledgeMapper.selectById(10L)).thenReturn(knowledge);

        knowledgeService.createNote(10L, "我的笔记", "这是一篇关于 RAG 的笔记内容,记录我的学习心得。", null);

        // 归属校验通过 + 同步索引(写优先:写完立刻可检索)
        verify(fileMapper).saveFile(any());
        verify(documentService).indexPlainText(any(), eq("这是一篇关于 RAG 的笔记内容,记录我的学习心得。"));

        // 越权:别人建笔记必须拒绝
        UserEntity other = new UserEntity();
        other.setId(2L);
        other.setUsername("hacker");
        UserContext.set(other);
        assertThrows(BusinessException.class, () -> knowledgeService.createNote(10L, "hack", "x", null));
    }

    @Test
    void exportShouldContainNoteContentAsBlockquote() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("tester");
        UserContext.set(user);

        KnowledgeEntity knowledge = new KnowledgeEntity();
        knowledge.setId(10L);
        knowledge.setUserId(1L);
        knowledge.setTitle("我的知识");
        knowledge.setAuthor("tester");
        when(knowledgeMapper.selectByUserId(1L)).thenReturn(List.of(knowledge));

        // 一条 AI 来源笔记(SUCCESS,带切片)+ 一条普通 md 上传文件(不应导出正文,只有清单行)
        FileEntity note = new FileEntity();
        note.setId(100L);
        note.setFileName("AI 总结笔记");
        note.setFileType("text/markdown;source=ai-chat");
        note.setStatus("SUCCESS");
        FileEntity uploadedMd = new FileEntity();
        uploadedMd.setId(101L);
        uploadedMd.setFileName("讲义.md");
        uploadedMd.setFileType("text/markdown");
        uploadedMd.setStatus("SUCCESS");
        when(fileMapper.selectFileByKnowledgeId(10L)).thenReturn(List.of(note, uploadedMd));

        ChunkEntity c1 = new ChunkEntity();
        c1.setChunkIndex(0);
        c1.setContent("第一段:RAG 的召回阶段。");
        ChunkEntity c2 = new ChunkEntity();
        c2.setChunkIndex(1);
        c2.setContent("第二段:重排与生成。");
        when(chunkMapper.selectByFileId(100L)).thenReturn(List.of(c2, c1)); // 乱序给,验证按 chunkIndex 拼
        when(chunkMapper.selectByFileId(101L)).thenReturn(null);

        String md = knowledgeService.exportMarkdown();

        // 笔记正文以引用块形式出现在导出中,且切片按序拼接
        assertTrue(md.contains("> 第一段:RAG 的召回阶段。"), "笔记第一段应以引用块导出");
        assertTrue(md.contains("> 第二段:重排与生成。"), "笔记第二段应以引用块导出");
        assertTrue(md.indexOf("第一段") < md.indexOf("第二段"), "切片应按 chunkIndex 顺序拼接");
        // 普通上传的 md 文件:只有清单行,不拼正文(无 chunk)
        assertTrue(md.contains("讲义.md [SUCCESS]"), "上传文件应保留清单行");
    }
}
