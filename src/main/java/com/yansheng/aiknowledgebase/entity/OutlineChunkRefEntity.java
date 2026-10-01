package com.yansheng.aiknowledgebase.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 节点 ↔ 真实 Chunk 关联(source_chunks 溯源)。
 * B 方案:只关联真实正文/后代 Chunk,空父标题不产生空关联;不参与检索路由(Phase2 才挂索引)。
 */
@Setter
@Getter
public class OutlineChunkRefEntity {

    private Long id;

    private Long nodeId;

    private Long chunkId;

    /** 冗余 chunk 序号,便于溯源排序 */
    private Integer chunkIndex;

    private LocalDateTime createTime;
}