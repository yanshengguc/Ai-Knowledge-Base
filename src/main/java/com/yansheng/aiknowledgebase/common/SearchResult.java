package com.yansheng.aiknowledgebase.common;

public class SearchResult {
    Long documentId;
    Long chunkId;
    String content;
    Double score;
    public SearchResult(){}
    public SearchResult(Long documentId, Long chunkId, String content, Double score) {
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.content = content;
        this.score = score;
    }

    public Long getDocumentId() {
        return documentId;
    }
    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }
    public Long getChunkId() {
        return chunkId;
    }
    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }
    public String getContent() {
        return content;
    }
    public void setContent(String content) {
        this.content = content;
    }
    public Double getScore() {
        return score;
    }
    public void setScore(Double score) {
        this.score = score;
    }
}
