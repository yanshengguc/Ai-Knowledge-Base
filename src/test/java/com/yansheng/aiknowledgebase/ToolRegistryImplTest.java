package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.common.tool.Tool;
import com.yansheng.aiknowledgebase.common.tool.ToolRegistry;
import com.yansheng.aiknowledgebase.common.tool.ToolRegistryImpl;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryImplTest {

    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        // 当前 ToolRegistryImpl 使用构造器注入 List<Tool>
        // 单元测试不启动 Spring，所以手动传入空 List
        toolRegistry = new ToolRegistryImpl(new ArrayList<>());
    }

    /**
     * 测试：注册 Tool 后可以根据名称正确获取
     */
    @Test
    void shouldRegisterAndGetTool() {

        Tool tool = new TestTool("file_trace");

        toolRegistry.registerTool(tool);

        Tool result = toolRegistry.getTool("file_trace");

        assertNotNull(result);
        assertSame(tool, result);
    }

    /**
     * 测试：获取不存在的 Tool 时抛出 BusinessException
     */
    @Test
    void shouldThrowExceptionWhenToolNotFound() {

        assertThrows(
                BusinessException.class,
                () -> toolRegistry.getTool("not_exist")
        );
    }

    /**
     * 测试：可以同时注册多个不同 Tool
     */
    @Test
    void shouldRegisterMultipleTools() {

        Tool fileTraceTool = new TestTool("file_trace");
        Tool anotherTool = new TestTool("test_tool");

        toolRegistry.registerTool(fileTraceTool);
        toolRegistry.registerTool(anotherTool);

        assertSame(
                fileTraceTool,
                toolRegistry.getTool("file_trace")
        );

        assertSame(
                anotherTool,
                toolRegistry.getTool("test_tool")
        );
    }

    /**
     * 测试：重复注册相同名称的 Tool 时抛出 BusinessException
     */
    @Test
    void shouldThrowExceptionWhenRegisterDuplicateTool() {

        Tool tool1 = new TestTool("file_trace");
        Tool tool2 = new TestTool("file_trace");

        toolRegistry.registerTool(tool1);

        assertThrows(
                BusinessException.class,
                () -> toolRegistry.registerTool(tool2)
        );
    }

    /**
     * 测试用 Tool
     * 用于隔离 ToolRegistry，避免引入真实业务 Service 的依赖。
     */
    static class TestTool implements Tool {

        private final String name;

        TestTool(String name) {
            this.name = name;
        }

        @Override
        public String getToolName() {
            return name;
        }

        @Override
        public String getToolDescription() {
            return "测试工具";
        }

        @Override
        public String execute(Map<String, Object> params) {
            return "success";
        }
    }
}