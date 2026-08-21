package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.common.tool.Tool;
import com.yansheng.aiknowledgebase.common.tool.ToolRegistry;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.service.FunctionCallingService;
import com.yansheng.aiknowledgebase.utils.UserContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

/**
 * 工具集扩展验证(8/19):
 * 1. 注册中心应有 4 个工具(file_search/file_trace/knowledge_stats/time_now)
 * 2. 模型能"自主选工具":
 *    - 问时间 → 应调用 time_now(不查知识库)
 *    - 问知识库统计 → 应调用 knowledge_stats
 * 观察点:控制台 ">>> xxx被调用" 日志
 */
@SpringBootTest
@Tag("e2e")
@ActiveProfiles("local")
class ToolExtensionVerifyTest {

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private FunctionCallingService functionCallingService;

    @Test
    void registryHasFourTools() {
        List<Tool> tools = toolRegistry.getAllTools();
        System.out.println("=== 注册中心工具清单 ===");
        for (Tool t : tools) {
            System.out.println("  - " + t.getToolName());
        }
        assert tools.size() >= 4 : "工具数应 >= 4,实际 " + tools.size();
    }

    @Test
    void modelCallsTimeNow() {
        // 模拟登录上下文(与 JWT 过滤器行为一致)
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("verify_tool");
        UserContext.set(user);
        try {
            String prompt = "今天是几号?星期几?请用工具查一下准确时间。";
            String result = functionCallingService.execute(prompt);
            System.out.println("=== 问时间 最终回答 ===");
            System.out.println(result);
            // 观察控制台应出现 ">>> time_now被调用"
        } finally {
            UserContext.remove();
        }
    }

    @Test
    void modelCallsKnowledgeStats() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("verify_tool");
        UserContext.set(user);
        try {
            String prompt = "帮我统计一下我的知识库概况:有多少知识、多少文件、多少切片、文件处理状态分布。";
            String result = functionCallingService.execute(prompt);
            System.out.println("=== 问统计 最终回答 ===");
            System.out.println(result);
            // 观察控制台应出现 ">>> knowledge_stats被调用"
        } finally {
            UserContext.remove();
        }
    }
}
