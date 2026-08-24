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
    /** 切片在文件内的序号(引用溯源到原文位置;向量路由出口批量补齐,旧缓存条目可能为 null) */
    Integer chunkIndex;
    public SearchResult(){}
    public SearchResult(Long fileId, Long chunkId, String content, Double score) {
        this.fileId = fileId;
        this.chunkId = chunkId;
        this.content = content;
        this.score = score;
    }

}
