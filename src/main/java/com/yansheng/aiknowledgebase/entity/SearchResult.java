package com.yansheng.aiknowledgebase.entity;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SearchResult {
    Long fileId;
    Long chunkId;
    String content;
    Double score;
    public SearchResult(){}
    public SearchResult(Long fileId, Long chunkId, String content, Double score) {
        this.fileId = fileId;
        this.chunkId = chunkId;
        this.content = content;
        this.score = score;
    }

}
