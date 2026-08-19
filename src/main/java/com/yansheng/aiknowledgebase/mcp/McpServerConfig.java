package com.yansheng.aiknowledgebase.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 配置:把 @Tool 注解的知识库工具注册为 ToolCallbackProvider,
 * Spring AI MCP Server starter 会自动收集并暴露给外部 MCP Client。
 */
@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider knowledgeMcpToolProvider(KnowledgeMcpTools tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }
}
