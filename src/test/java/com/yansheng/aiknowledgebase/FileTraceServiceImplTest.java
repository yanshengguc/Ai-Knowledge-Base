package com.yansheng.aiknowledgebase;


import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.service.FileTraceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("local")
class FileTraceServiceImplTest {

    @Autowired
    private FileTraceService fileTraceService;

    @Test
    void testExecute_withValidFileId() {
        Map<String, Object> params = new HashMap<>();
        params.put("fileId", 1L);

        String result = fileTraceService.execute(params);

        System.out.println("溯源工具返回结果: " + result);
        assertNotNull(result);
    }

    @Test
    void testExecute_missingFileId() {
        Map<String, Object> params = new HashMap<>();
        // 不放fileId

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileTraceService.execute(params));

        System.out.println("异常信息: " + exception.getMessage());
    }

    @Test
    void testExecute_invalidFileIdFormat() {
        Map<String, Object> params = new HashMap<>();
        params.put("fileId", "abc");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileTraceService.execute(params));

        System.out.println("异常信息: " + exception.getMessage());
    }

    @Test
    void testExecute_fileNotFound() {
        Map<String, Object> params = new HashMap<>();
        params.put("fileId", 999999L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileTraceService.execute(params));

        System.out.println("异常信息: " + exception.getMessage());
    }
}