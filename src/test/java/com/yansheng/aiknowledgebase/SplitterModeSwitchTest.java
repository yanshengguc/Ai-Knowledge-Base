package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.service.splitter.DocumentSplitter;
import com.yansheng.aiknowledgebase.service.splitter.SimpleTextSplitter;
import com.yansheng.aiknowledgebase.service.splitter.StructureAwareSplitter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 splitter.mode 配置切换真实生效(对比实验的前提) */
@SpringBootTest
@ActiveProfiles("local")
class SplitterModeSwitchTest {

    @Autowired
    private DocumentSplitter splitter;

    @Test
    void 当前的活跃切片器与配置一致() {
        String mode = System.getProperty("splitter.mode", "structure");
        System.out.println(">>> splitter.mode=" + mode + " | active bean=" + splitter.getClass().getSimpleName());
        if ("simple".equals(mode)) {
            assertTrue(splitter instanceof SimpleTextSplitter, "预期 SimpleTextSplitter,实际 " + splitter.getClass());
        } else {
            assertTrue(splitter instanceof StructureAwareSplitter, "预期 StructureAwareSplitter,实际 " + splitter.getClass());
        }
    }
}
