package com.yansheng.aiknowledgebase.entity;

/**
 * Agent Loop 工具调用轨迹事件:随 SSE "tool" 事件推给前端做时间线可视化。
 * args/summary 在服务层已截断,前端只渲染不再加工。
 */
public record ToolTraceEvent(
        int step,
        String tool,
        String args,
        String summary) {
}
