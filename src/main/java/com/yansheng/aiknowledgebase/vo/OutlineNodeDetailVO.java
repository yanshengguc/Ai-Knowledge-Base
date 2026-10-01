package com.yansheng.aiknowledgebase.vo;

import lombok.Data;

import java.util.List;

/** Outline 节点详情:节点元信息 + source_chunks 溯源列表 */
@Data
public class OutlineNodeDetailVO {
    private Long id;
    private Long fileId;
    private Integer nodeIndex;
    private Integer level;
    private String title;
    private String headingPath;
    private List<OutlineSourceChunkVO> sourceChunks;
}