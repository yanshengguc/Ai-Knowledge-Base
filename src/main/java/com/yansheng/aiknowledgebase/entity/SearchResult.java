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
    /** 来源文件名(检索出口统一填充,供前端引用面板展示出处) */
    String fileName;
    public SearchResult(){}
    public SearchResult(Long fileId, Long chunkId, String content, Double score) {
        this.fileId = fileId;
        this.chunkId = chunkId;
        this.content = content;
        this.score = score;
    }

}
