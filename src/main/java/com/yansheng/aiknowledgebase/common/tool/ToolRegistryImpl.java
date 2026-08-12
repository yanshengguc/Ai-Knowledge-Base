package com.yansheng.aiknowledgebase.common.tool;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ToolRegistryImpl implements ToolRegistry {

    private final Map<String, Tool> tools = new HashMap<>();

    public ToolRegistryImpl(List<Tool> tools) {
        for (Tool tool : tools) {
            registerTool(tool);
        }
    }

    @Override
    public void registerTool(Tool tool) {
        String toolName = tool.getToolName();

        if (tools.containsKey(toolName)) {
            throw new BusinessException("工具已存在: " + toolName);
        }

        tools.put(toolName, tool);
    }

    @Override
    public Tool getTool(String name) {
        Tool tool = tools.get(name);

        if (tool == null) {
            throw new BusinessException("工具不存在: " + name);
        }

        return tool;
    }

    @Override
    public List<Tool> getAllTools() {
        return new ArrayList<>(tools.values());
    }
}