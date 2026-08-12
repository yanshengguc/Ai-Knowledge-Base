package com.yansheng.aiknowledgebase.common.tool;

import java.util.Map;
import java.util.List;
public interface ToolRegistry {
    void registerTool(Tool tool);
   Tool getTool(String name);
   List<Tool> getAllTools();
}
