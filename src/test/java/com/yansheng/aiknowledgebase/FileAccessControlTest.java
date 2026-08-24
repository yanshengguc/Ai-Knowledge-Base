package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.DocumentService;
import com.yansheng.aiknowledgebase.service.OssService;
import com.yansheng.aiknowledgebase.service.RetrievalService;
import com.yansheng.aiknowledgebase.service.VectorStoreService;
import com.yansheng.aiknowledgebase.service.impl.FileServiceImpl;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.FileVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P0-权限: 文件接口 IDOR 修复回归。
 * 攻防实测曾证实:任意登录用户可读他人文件详情与文件清单(含 OSS 地址)。
 * 修复后口径:文件所属知识的 author 必须等于当前用户名,否则"权限不足"。
 */
class FileAccessControlTest {

    private FileMapper fileMapper;
    private KnowledgeMapper knowledgeMapper;
    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        fileMapper = mock(FileMapper.class);
        knowledgeMapper = mock(KnowledgeMapper.class);
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

        FileEntity ownFile = new FileEntity();
        ownFile.setId(101L);
        ownFile.setKnowledgeId(10L);
        ownFile.setFileName("my.pdf");
        when(fileMapper.selectById(101L)).thenReturn(ownFile);

        FileEntity othersFile = new FileEntity();
        othersFile.setId(201L);
        othersFile.setKnowledgeId(20L);
        othersFile.setFileName("secret.pdf");
        when(fileMapper.selectById(201L)).thenReturn(othersFile);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void ownFileDetailReadable() {
        FileVO vo = fileService.getFileById(101L);
        assertEquals("my.pdf", vo.getFileName());
    }

    @Test
    void othersFileDetailDenied() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> fileService.getFileById(201L));
        assertEquals("权限不足", e.getMessage());
    }

    @Test
    void othersFileListDenied() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> fileService.listByKnowledgeId(20L));
        assertEquals("权限不足", e.getMessage());
    }

    @Test
    void ownFileListReadable() {
        when(fileMapper.selectFileByKnowledgeId(10L)).thenReturn(List.of());
        assertEquals(0, fileService.listByKnowledgeId(10L).size());
    }

    @Test
    void fileWithoutKnowledgeDenied() {
        FileEntity orphan = new FileEntity();
        orphan.setId(301L);
        orphan.setKnowledgeId(999L);
        when(fileMapper.selectById(301L)).thenReturn(orphan);
        when(knowledgeMapper.selectById(999L)).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> fileService.getFileById(301L));
        assertEquals("知识不存在", e.getMessage());
    }

    @Test
    void noUserContextDenied() {
        UserContext.remove();
        BusinessException e = assertThrows(BusinessException.class,
                () -> fileService.getFileById(101L));
        assertEquals("权限不足", e.getMessage());
    }
}
