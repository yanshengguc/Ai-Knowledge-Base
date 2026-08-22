package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.dto.KnowledgeAddDTO;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 防脏数据回归:addKnowledge 空标题/空内容必须拒绝(防 F1 类"POST /knowledge {action:list}"写空数据)。
 * createNote:校验 + 归属 + 同步索引(写优先)。
 */
class KnowledgeAddValidationTest {

    private KnowledgeMapper knowledgeMapper;
    private FileMapper fileMapper;
    private DocumentService documentService;
    private KnowledgeServiceImpl knowledgeService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        knowledgeMapper = mock(KnowledgeMapper.class);
        fileMapper = mock(FileMapper.class);
        documentService = mock(DocumentService.class);
        knowledgeService = new KnowledgeServiceImpl(
                knowledgeMapper,
                fileMapper,
                mock(ChunkMapper.class),
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
        assertThrows(BusinessException.class, () -> knowledgeService.createNote(1L, " ", "内容"));
        assertThrows(BusinessException.class, () -> knowledgeService.createNote(1L, "标题", null));
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

        knowledgeService.createNote(10L, "我的笔记", "这是一篇关于 RAG 的笔记内容,记录我的学习心得。");

        // 归属校验通过 + 同步索引(写优先:写完立刻可检索)
        verify(fileMapper).saveFile(any());
        verify(documentService).indexPlainText(any(), eq("这是一篇关于 RAG 的笔记内容,记录我的学习心得。"));

        // 越权:别人建笔记必须拒绝
        UserEntity other = new UserEntity();
        other.setId(2L);
        other.setUsername("hacker");
        UserContext.set(other);
        assertThrows(BusinessException.class, () -> knowledgeService.createNote(10L, "hack", "x"));
    }
}
