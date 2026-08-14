package com.yansheng.aiknowledgebase;


import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.service.FileSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class FileSearchServiceImplTest {

    @Autowired
    private FileSearchService fileSearchService;

    @Test
    void testExecute_withValidQuery() {
        Map<String, Object> params = new HashMap<>();
        params.put("query", "用户认证是怎么实现的");

        String result = fileSearchService.execute(params);

        System.out.println("file_search工具返回结果: " + result);
        assertNotNull(result);
        // 返回的是合法 JSON,且带 results 字段(向量库有数据时含 fileId)
        assertTrue(result.contains("results"));
    }

    @Test
    void testExecute_missingQuery() {
        Map<String, Object> params = new HashMap<>();
        // 不放 query

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileSearchService.execute(params));

        System.out.println("异常信息: " + exception.getMessage());
    }

    @Test
    void testExecute_blankQuery() {
        Map<String, Object> params = new HashMap<>();
        params.put("query", "   ");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileSearchService.execute(params));

        System.out.println("异常信息: " + exception.getMessage());
    }
}
