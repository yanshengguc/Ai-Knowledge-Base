package com.yansheng.aiknowledgebase;


import com.yansheng.aiknowledgebase.common.tool.Tool;
import com.yansheng.aiknowledgebase.common.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class ToolRegistrySpringTest {

    @Autowired
    private ToolRegistry toolRegistry;

    /**
     * 验证 Spring 能够创建 ToolRegistry
     */
    @Test
    void shouldLoadToolRegistryFromSpring() {

        assertNotNull(toolRegistry);
    }

    /**
     * 验证 Spring 自动发现 FileTraceServiceImpl，
     * 并通过 List<Tool> 注入 ToolRegistry。
     */
    @Test
    void shouldAutoRegisterFileTraceTool() {

        Tool tool = toolRegistry.getTool("file_trace");

        assertNotNull(tool);
        assertEquals("file_trace", tool.getToolName());
    }
}