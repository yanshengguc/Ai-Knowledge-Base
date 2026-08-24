package com.yansheng.aiknowledgebase;


import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.service.FileTraceService;
import org.junit.jupiter.api.Assumptions;
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

    @Autowired
    private FileMapper fileMapper;

    @Test
    void testExecute_withValidFileId() {
        // 防御式取数:固定 fileId=1 依赖历史数据(体验时可能被删掉),改为动态取库里真实存在的文件;
        // 库为空时跳过而非失败——本用例验证的是"正常路径能跑通",不是"必须有数据"
        FileEntity any = fileMapper.selectFirstFile();
        Assumptions.assumeTrue(any != null, "库中无文件,跳过");
        Map<String, Object> params = new HashMap<>();
        params.put("fileId", any.getId());

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