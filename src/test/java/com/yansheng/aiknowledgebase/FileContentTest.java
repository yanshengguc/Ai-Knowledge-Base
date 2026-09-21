package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.OssService;
import com.yansheng.aiknowledgebase.service.impl.FileServiceImpl;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.FileContentVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B-112 文件在线预览(FileServiceImpl.getFileContent)单元测试:
 * 作者归属校验 + 类型白名单(仅 md 返回原文) + OSS 读取降级。
 */
class FileContentTest {

    private FileMapper fileMapper;
    private KnowledgeMapper knowledgeMapper;
    private OssService ossService;
    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        fileMapper = mock(FileMapper.class);
        knowledgeMapper = mock(KnowledgeMapper.class);
        ossService = mock(OssService.class);
        fileService = new FileServiceImpl(
                null, ossService, knowledgeMapper, fileMapper, null, null, null, null);

        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("ys");
        UserContext.set(user);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private FileEntity file(Long id, String fileName) {
        FileEntity f = new FileEntity();
        f.setId(id);
        f.setFileName(fileName);
        f.setFileType("application/octet-stream");
        f.setFileUrl("https://bucket.oss.example.com/uuid_" + fileName);
        f.setKnowledgeId(10L);
        return f;
    }

    private KnowledgeEntity knowledge(String author) {
        KnowledgeEntity k = new KnowledgeEntity();
        k.setId(10L);
        k.setAuthor(author);
        return k;
    }

    @Test
    void testMdFile_returnsContent() {
        when(fileMapper.selectById(100L)).thenReturn(file(100L, "设计模式.md"));
        when(knowledgeMapper.selectById(10L)).thenReturn(knowledge("ys"));
        when(ossService.getContent("https://bucket.oss.example.com/uuid_设计模式.md"))
                .thenReturn("# 单例模式\n饿汉式...");

        FileContentVO vo = fileService.getFileContent(100L);

        assertEquals("设计模式.md", vo.getFileName());
        assertEquals("# 单例模式\n饿汉式...", vo.getContent());
    }

    @Test
    void testNotOwner_rejected() {
        when(fileMapper.selectById(100L)).thenReturn(file(100L, "a.md"));
        when(knowledgeMapper.selectById(10L)).thenReturn(knowledge("someone-else"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.getFileContent(100L));
        assertTrue(ex.getMessage().contains("权限"));
        Mockito.verifyNoInteractions(ossService);
    }

    @Test
    void testKnowledgeNotExist_rejected() {
        when(fileMapper.selectById(100L)).thenReturn(file(100L, "a.md"));
        when(knowledgeMapper.selectById(10L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.getFileContent(100L));
        assertTrue(ex.getMessage().contains("知识不存在"));
    }

    @Test
    void testFileNotExist_rejected() {
        when(fileMapper.selectById(404L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.getFileContent(404L));
        assertTrue(ex.getMessage().contains("文件不存在"));
    }

    @Test
    void testPdfDocx_contentNull_neverTouchesOss() {
        when(fileMapper.selectById(200L)).thenReturn(file(200L, "教材.pdf"));
        when(knowledgeMapper.selectById(10L)).thenReturn(knowledge("ys"));

        FileContentVO vo = fileService.getFileContent(200L);

        assertNull(vo.getContent());
        Mockito.verifyNoInteractions(ossService);

        when(fileMapper.selectById(201L)).thenReturn(file(201L, "实验.docx"));
        FileContentVO vo2 = fileService.getFileContent(201L);
        assertNull(vo2.getContent());
    }

    @Test
    void testOssFailure_wrappedAsBusinessException() {
        when(fileMapper.selectById(100L)).thenReturn(file(100L, "broken.md"));
        when(knowledgeMapper.selectById(10L)).thenReturn(knowledge("ys"));
        when(ossService.getContent("https://bucket.oss.example.com/uuid_broken.md"))
                .thenThrow(new BusinessException("原文读取失败"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.getFileContent(100L));
        assertTrue(ex.getMessage().contains("原文读取失败"));
    }
}
