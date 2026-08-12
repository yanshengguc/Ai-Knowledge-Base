package com.yansheng.aiknowledgebase.common.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

public class ToolCallbackAdapter implements ToolCallback {

    private final Tool tool;
    private final ObjectMapper objectMapper;
    private final ToolDefinition toolDefinition;

    public ToolCallbackAdapter(
            Tool tool,
            ObjectMapper objectMapper,
            String inputSchema) {

        this.tool = tool;
        this.objectMapper = objectMapper;

        this.toolDefinition = ToolDefinition.builder()
                .name(tool.getToolName())
                .description(tool.getToolDescription())
                .inputSchema(inputSchema)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        try {
            Map<String, Object> params =
                    objectMapper.readValue(
                            toolInput,
                            new TypeReference<Map<String, Object>>() {}
                    );

            return tool.execute(params);

        } catch (Exception e) {
            throw new RuntimeException(
                    "工具执行失败: " + tool.getToolName(), e);
        }
    }
}