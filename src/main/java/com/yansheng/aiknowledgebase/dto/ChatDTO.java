package com.yansheng.aiknowledgebase.dto;

public class ChatDTO {
    private String message;

    /**
     * 是否启用联网搜索(前端"🌐联网"开关控制,用户显式授权)
     * true = 回答前先联网搜索,把最新信息注入上下文
     */
    private boolean enableWebSearch;

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
}
