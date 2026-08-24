package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.common.tool.Tool;
import com.yansheng.aiknowledgebase.common.tool.ToolRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

/**
 * 工具集扩展验证:
 * 注册中心应有 4 个工具(file_search/file_trace/knowledge_stats/time_now)。
 * "模型自主选工具"行为已由 EvalHarnessTest(数据集+准确率断言)覆盖,不再重复观察式用例。
 */
@SpringBootTest
@Tag("e2e")
@ActiveProfiles("local")
class ToolExtensionVerifyTest {

    @Autowired
    private ToolRegistry toolRegistry;

    @Test
    void registryHasFourTools() {
        List<Tool> tools = toolRegistry.getAllTools();
        System.out.println("=== 注册中心工具清单 ===");
        for (Tool t : tools) {
            System.out.println("  - " + t.getToolName());
        }
        assert tools.size() >= 4 : "工具数应 >= 4,实际 " + tools.size();
    }
}
