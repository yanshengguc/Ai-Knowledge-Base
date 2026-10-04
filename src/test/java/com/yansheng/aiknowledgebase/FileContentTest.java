package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.OssService;
import com.yansheng.aiknowledgebase.service.impl.FileServiceImpl;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.FileContentVO;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private ChunkMapper chunkMapper;
    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        fileMapper = mock(FileMapper.class);
        knowledgeMapper = mock(KnowledgeMapper.class);
        ossService = mock(OssService.class);
        chunkMapper = mock(ChunkMapper.class);
        fileService = new FileServiceImpl(
                null, ossService, knowledgeMapper, fileMapper, chunkMapper, null, null, null);

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

    /** 构造一个切片(chunkIndex + content) */
    private ChunkEntity chunk(int index, String content) {
        ChunkEntity c = new ChunkEntity();
        c.setChunkIndex(index);
        c.setContent(content);
        return c;
    }

    @Test
    void testNote_noOss_readsChunksOrdered() {
        FileEntity note = file(300L, "我的笔记");
        note.setFileUrl(null);
        note.setFileType("text/markdown;source=manual");
        when(fileMapper.selectById(300L)).thenReturn(note);
        when(knowledgeMapper.selectById(10L)).thenReturn(knowledge("ys"));
        // 故意乱序返回,验证按 chunkIndex 升序拼接
        when(chunkMapper.selectByFileId(300L))
                .thenReturn(Arrays.asList(chunk(2, "第三段"), chunk(0, "第一段"), chunk(1, "第二段")));

        FileContentVO vo = fileService.getFileContent(300L);

        assertEquals("第一段\n第二段\n第三段", vo.getContent());
        Mockito.verifyNoInteractions(ossService);
    }

    @Test
    void testUploadedMd_withOssUrl_stillReadsOss() {
        // 浏览器上传的 .md:contentType 可能恰为 text/markdown,但有 OSS url → 仍走 OSS,不读 chunk
        FileEntity uploaded = file(400L, "上传.md");
        uploaded.setFileType("text/markdown");
        when(fileMapper.selectById(400L)).thenReturn(uploaded);
        when(knowledgeMapper.selectById(10L)).thenReturn(knowledge("ys"));
        when(ossService.getContent("https://bucket.oss.example.com/uuid_上传.md"))
                .thenReturn("# 上传正文");

        FileContentVO vo = fileService.getFileContent(400L);

        assertEquals("# 上传正文", vo.getContent());
        Mockito.verifyNoInteractions(chunkMapper);
    }

    @Test
    void testNote_noChunks_returnsEmptyStringNotNull() {
        // 契约:笔记无切片时返回空串而非 null,前端据此区分"正文为空"与"格式不支持预览"
        FileEntity note = file(300L, "空笔记");
        note.setFileUrl(null);
        note.setFileType("text/markdown;source=manual");
        when(fileMapper.selectById(300L)).thenReturn(note);
        when(knowledgeMapper.selectById(10L)).thenReturn(knowledge("ys"));
        when(chunkMapper.selectByFileId(300L)).thenReturn(Collections.emptyList());

        FileContentVO vo = fileService.getFileContent(300L);

        assertEquals("", vo.getContent());
        assertNotNull(vo.getContent());
        Mockito.verifyNoInteractions(ossService);
    }
}
