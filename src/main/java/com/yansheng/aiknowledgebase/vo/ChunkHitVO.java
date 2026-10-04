package com.yansheng.aiknowledgebase.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * B-110 命中片段窄 VO:只暴露定位高亮所需的最小字段,隐藏 chunkId 等内部标识。
 */
@Setter
@Getter
public class ChunkHitVO {
    private Long fileId;
    private String fileName;
    private Integer chunkIndex;
    private String content;
    private Double score;
}
