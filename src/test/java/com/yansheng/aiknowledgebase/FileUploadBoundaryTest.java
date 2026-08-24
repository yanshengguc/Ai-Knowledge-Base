package com.yansheng.aiknowledgebase;

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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P0-数据: 文件上传校验的三类用例(正常/边界/异常)。
 * 安全线: 20MB 上限(> 判断,恰好 20MB 放行) + 后缀白名单 .pdf/.docx/.md(大小写不敏感)。
 * 白名单一旦被绕过(.exe/.html/双后缀),恶意文件即可上 OSS。
 */
class FileUploadBoundaryTest {

    private static final long TWENTY_MB = 20L * 1024 * 1024;

    private OssService ossService;
    private KnowledgeMapper knowledgeMapper;
    private FileMapper fileMapper;
    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        ossService = mock(OssService.class);
        knowledgeMapper = mock(KnowledgeMapper.class);
        fileMapper = mock(FileMapper.class);
        fileService = new FileServiceImpl(mock(DocumentService.class), ossService,
                knowledgeMapper, fileMapper, mock(ChunkMapper.class),
                Runnable::run, mock(RetrievalService.class), mock(VectorStoreService.class));

        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("tester");
        UserContext.set(user);

        KnowledgeEntity knowledge = new KnowledgeEntity();
        knowledge.setId(10L);
        knowledge.setUserId(1L);
        knowledge.setAuthor("tester");
        when(knowledgeMapper.selectById(10L)).thenReturn(knowledge);
        when(fileMapper.selectFileByKnowledgeId(10L)).thenReturn(List.of());
        when(ossService.upload(any())).thenReturn("http://oss/mock/file");
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private MultipartFile file(String name, long size, boolean empty) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(empty);
        when(file.getSize()).thenReturn(size);
        when(file.getOriginalFilename()).thenReturn(name);
        return file;
    }

    // ===== 正常 =====

    @Test
    void normalPdfAcceptedAndUploadedToOss() {
        fileService.uploadFile(file("报告.pdf", 2048L, false), 10L);
        verify(ossService).upload(any());
    }

    // ===== 边界 =====

    @Test
    void exactlyTwentyMbIsAccepted() {
        // 判断条件是 > maxSize,恰好 20MB 应放行
        fileService.uploadFile(file("edge.pdf", TWENTY_MB, false), 10L);
        verify(ossService).upload(any());
    }

    @Test
    void twentyMbPlusOneByteRejected() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> fileService.uploadFile(file("edge.pdf", TWENTY_MB + 1, false), 10L));
        assertEquals("文件大小不能超过20MB", e.getMessage());
        verify(ossService, never()).upload(any());
    }

    @Test
    void uppercasePdfExtensionAccepted() {
        // 白名单 toLowerCase 匹配,.PDF 应放行
        fileService.uploadFile(file("REPORT.PDF", 100L, false), 10L);
        verify(ossService).upload(any());
    }

    // ===== 异常/恶意输入 =====

    @Test
    void exeRejected() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> fileService.uploadFile(file("shell.exe", 100L, false), 10L));
        assertEquals("仅支持 pdf/docx/md 格式文件", e.getMessage());
        verify(ossService, never()).upload(any());
    }

    @Test
    void doubleExtensionPdfExeRejected() {
        // 伪装成 pdf 的 exe:后缀看最后一段,必须拒绝
        assertThrows(BusinessException.class,
                () -> fileService.uploadFile(file("shell.pdf.exe", 100L, false), 10L));
        verify(ossService, never()).upload(any());
    }

    @Test
    void htmlRejected() {
        assertThrows(BusinessException.class,
                () -> fileService.uploadFile(file("xss.html", 100L, false), 10L));
        verify(ossService, never()).upload(any());
    }

    @Test
    void nullFilenameRejected() {
        assertThrows(BusinessException.class,
                () -> fileService.uploadFile(file(null, 100L, false), 10L));
        verify(ossService, never()).upload(any());
    }

    @Test
    void emptyFileRejected() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> fileService.uploadFile(file("empty.pdf", 0L, true), 10L));
        assertEquals("文件不能为空", e.getMessage());
        verify(ossService, never()).upload(any());
    }

    @Test
    void othersKnowledgeRejected() {
        KnowledgeEntity others = new KnowledgeEntity();
        others.setId(20L);
        others.setUserId(999L);
        others.setAuthor("attacker");
        when(knowledgeMapper.selectById(20L)).thenReturn(others);
        assertThrows(BusinessException.class,
                () -> fileService.uploadFile(file("a.pdf", 100L, false), 20L));
        verify(ossService, never()).upload(any());
    }
}
