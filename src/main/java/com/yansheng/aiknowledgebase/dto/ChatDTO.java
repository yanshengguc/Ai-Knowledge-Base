package com.yansheng.aiknowledgebase.dto;

public class ChatDTO {
    private String message;

    /**
     * 是否启用联网搜索(前端"🌐联网"开关控制,用户显式授权)
     * true = 回答前先联网搜索,把最新信息注入上下文
     */
    private boolean enableWebSearch;

    /**
     * 是否启用 Agent 模式(前端"🤖 Agent"开关控制)
     * true = 走手写 ReAct 循环,模型自主决策调用工具,工具轨迹随 SSE tool 事件推给前端
     */
    private boolean enableAgent;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isEnableWebSearch() {
        return enableWebSearch;
    }

    public void setEnableWebSearch(boolean enableWebSearch) {
        this.enableWebSearch = enableWebSearch;
    }

    public boolean isEnableAgent() {
        return enableAgent;
    }

    public void setEnableAgent(boolean enableAgent) {
        this.enableAgent = enableAgent;
    }
}
