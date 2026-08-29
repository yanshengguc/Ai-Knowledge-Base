package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.entity.ToolTraceEvent;

import java.util.function.Consumer;

public interface FunctionCallingService {

    String execute(String prompt);

    /**
     * 带工具轨迹与用量记账的执行:每轮模型决策调用工具后回调 onTool(供 SSE 时间线),
     * userId 非空时按次记录 token 用量(Agent 模式一次对话含多次模型调用,记账必须覆盖)。
     */
    String execute(Long userId, String prompt, Consumer<ToolTraceEvent> onTool);
}
