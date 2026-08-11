package com.yansheng.aiknowledgebase.entity;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
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

}
