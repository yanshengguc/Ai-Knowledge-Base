package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.entity.UserEntity;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * B2 一致性回归:同名覆盖改为"新版本成功后再清旧版"——
 * 成功 → 清理旧版;失败 → 保留旧版(不丢数据)。
 */
class FileServiceImplTest {

    private DocumentService documentService;
    private OssService ossService;
    private KnowledgeMapper knowledgeMapper;
    private FileMapper fileMapper;
    private ChunkMapper chunkMapper;
    private Executor executor;
    private RetrievalService retrievalService;
    private VectorStoreService vectorStoreService;
    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);
        ossService = mock(OssService.class);
        knowledgeMapper = mock(KnowledgeMapper.class);
        fileMapper = mock(FileMapper.class);
        chunkMapper = mock(ChunkMapper.class);
        executor = mock(Executor.class);
        retrievalService = mock(RetrievalService.class);
        vectorStoreService = mock(VectorStoreService.class);

        // 让"异步"任务立即同步执行,便于断言回调逻辑
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(executor).execute(any());

        fileService = new FileServiceImpl(documentService, ossService, knowledgeMapper,
                fileMapper, chunkMapper, executor, retrievalService, vectorStoreService);

        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("tester");
        UserContext.set(user);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private KnowledgeEntity knowledge() {
        KnowledgeEntity k = new KnowledgeEntity();
        k.setId(10L);
        k.setUserId(1L);
        k.setAuthor("tester");
        return k;
    }

    private FileEntity oldFile() {
        FileEntity f = new FileEntity();
        f.setId(99L);
        f.setFileName("report.pdf");
        f.setFileUrl("http://oss/old/report.pdf");
        f.setKnowledgeId(10L);
        return f;
    }

    private MultipartFile pdf() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(2048L);
        when(file.getOriginalFilename()).thenReturn("report.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        try {
            when(file.getBytes()).thenReturn(new byte[2048]);
        } catch (Exception ignored) {
        }
        return file;
    }

    @Test
    void uploadSuccessShouldCleanOldVersion() {
        when(knowledgeMapper.selectById(10L)).thenReturn(knowledge());
        when(fileMapper.selectFileByKnowledgeId(10L)).thenReturn(List.of(oldFile()));
        when(ossService.upload(any())).thenReturn("http://oss/new/report.pdf");

        FileEntity entity = fileService.uploadFile(pdf(), 10L);
        assertNotNull(entity);

        // 新版本成功后:旧版切片/记录/OSS/向量 被清理,新记录置 SUCCESS
        verify(chunkMapper).deleteByFileId(99L);
        verify(fileMapper).deleteById(99L);
        verify(ossService).delete("http://oss/old/report.pdf");
        verify(vectorStoreService).deleteByFileId(99L);
        verify(fileMapper).updateStatus(any(), eq("SUCCESS"), isNull());
    }

    @Test
    void uploadFailureShouldKeepOldVersion() {
        when(knowledgeMapper.selectById(10L)).thenReturn(knowledge());
        when(fileMapper.selectFileByKnowledgeId(10L)).thenReturn(List.of(oldFile()));
        when(ossService.upload(any())).thenReturn("http://oss/new/report.pdf");
        doThrow(new RuntimeException("解析失败")).when(documentService).handleDocument(any(), any());

        FileEntity entity = fileService.uploadFile(pdf(), 10L);
        assertNotNull(entity);

        // 新版本失败:旧版保留(不丢数据),新记录置 FAILED
        verify(chunkMapper, never()).deleteByFileId(99L);
        verify(fileMapper, never()).deleteById(99L);
        verify(ossService, never()).delete(any());
        verify(fileMapper).updateStatus(any(), eq("FAILED"), eq("解析失败"));
    }
}
