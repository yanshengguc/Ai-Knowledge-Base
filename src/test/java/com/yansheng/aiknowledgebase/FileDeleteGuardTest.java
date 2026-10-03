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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * B-120② 回归:PROCESSING 状态文件禁删护栏。
 * 归属校验通过后、级联删除前拦截 PROCESSING,避免留下孤儿 chunk/向量;
 * SUCCESS/FAILED 删除路径与原有权限口径保持不变。
 */
class FileDeleteGuardTest {

    private OssService ossService;
    private KnowledgeMapper knowledgeMapper;
    private FileMapper fileMapper;
    private ChunkMapper chunkMapper;
    private VectorStoreService vectorStoreService;
    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        ossService = mock(OssService.class);
        knowledgeMapper = mock(KnowledgeMapper.class);
        fileMapper = mock(FileMapper.class);
        chunkMapper = mock(ChunkMapper.class);
        vectorStoreService = mock(VectorStoreService.class);
        fileService = new FileServiceImpl(mock(DocumentService.class), ossService, knowledgeMapper,
                fileMapper, chunkMapper, Runnable::run, mock(RetrievalService.class), vectorStoreService);

        UserEntity owner = new UserEntity();
        owner.setId(1L);
        owner.setUsername("owner");
        UserContext.set(owner);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private KnowledgeEntity mine() {
        KnowledgeEntity k = new KnowledgeEntity();
        k.setId(10L);
        k.setUserId(1L);
        k.setAuthor("owner");
        return k;
    }

    private FileEntity file(long id, String status) {
        FileEntity f = new FileEntity();
        f.setId(id);
        f.setKnowledgeId(10L);
        f.setFileName("doc.pdf");
        f.setFileUrl("http://oss/doc.pdf");
        f.setStatus(status);
        return f;
    }

    @Test
    void processingFileDeleteBlockedWithoutSideEffects() {
        when(fileMapper.selectById(50L)).thenReturn(file(50L, "PROCESSING"));
        when(knowledgeMapper.selectById(10L)).thenReturn(mine());

        BusinessException e = assertThrows(BusinessException.class,
                () -> fileService.deleteFile(50L));
        assertEquals("文件处理中,完成后才能删除", e.getMessage());

        verify(chunkMapper, never()).deleteByFileId(any());
        verify(fileMapper, never()).deleteById(any());
        verify(ossService, never()).delete(any());
        verify(vectorStoreService, never()).deleteByFileId(any());
    }

    @Test
    void successFileDeleteCascades() {
        when(fileMapper.selectById(51L)).thenReturn(file(51L, "SUCCESS"));
        when(knowledgeMapper.selectById(10L)).thenReturn(mine());

        fileService.deleteFile(51L);

        verify(chunkMapper).deleteByFileId(51L);
        verify(fileMapper).deleteById(51L);
        verify(ossService).delete("http://oss/doc.pdf");
        verify(vectorStoreService).deleteByFileId(51L);
    }

    @Test
    void failedFileDeleteCascades() {
        when(fileMapper.selectById(52L)).thenReturn(file(52L, "FAILED"));
        when(knowledgeMapper.selectById(10L)).thenReturn(mine());

        fileService.deleteFile(52L);

        verify(chunkMapper).deleteByFileId(52L);
        verify(fileMapper).deleteById(52L);
        verify(ossService).delete("http://oss/doc.pdf");
        verify(vectorStoreService).deleteByFileId(52L);
    }

    @Test
    void nonAuthorDeleteStillDenied() {
        when(fileMapper.selectById(53L)).thenReturn(file(53L, "PROCESSING"));
        KnowledgeEntity others = new KnowledgeEntity();
        others.setId(10L);
        others.setUserId(2L);
        others.setAuthor("attacker");
        when(knowledgeMapper.selectById(10L)).thenReturn(others);

        BusinessException e = assertThrows(BusinessException.class,
                () -> fileService.deleteFile(53L));
        assertEquals("无权删除该文件", e.getMessage());

        verify(chunkMapper, never()).deleteByFileId(any());
        verify(fileMapper, never()).deleteById(any());
        verify(ossService, never()).delete(any());
        verify(vectorStoreService, never()).deleteByFileId(any());
    }

    @Test
    void missingKnowledgeDeleteDenied() {
        when(fileMapper.selectById(54L)).thenReturn(file(54L, "SUCCESS"));
        when(knowledgeMapper.selectById(10L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> fileService.deleteFile(54L));
        assertEquals("无权删除该文件", e.getMessage());
    }

    @Test
    void missingFileDeleteDenied() {
        when(fileMapper.selectById(55L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> fileService.deleteFile(55L));
        assertEquals("文件不存在", e.getMessage());
    }
}
