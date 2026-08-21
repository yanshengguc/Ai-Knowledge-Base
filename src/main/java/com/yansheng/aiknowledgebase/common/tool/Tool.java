package com.yansheng.aiknowledgebase.common.tool;

import java.util.Map;

/**
 * function calling 工具契约接口
 * 所有工具实现类需实现此接口,由Spring自动扫描注册进工具Map
 */
public interface Tool {

    /**
     * 工具唯一标识,供模型识别调用、编排层按名字查找
     */
    String getToolName();

    /**
     * 工具描述 + 参数说明,用于组装成JSON schema供模型理解
     */
    String getToolDescription();

    /**
     * 工具的参数 JSON Schema(每个工具自带,新增工具无需改动编排层 —— 开闭原则)。
     */
    String getInputSchema();

    /**
     * 执行工具逻辑
     * @param params 编排层已解析好的参数键值对
     * @return 执行结果字符串,将被发回模型作为工具调用结果
     */
    String execute(Map<String, Object> params);
}