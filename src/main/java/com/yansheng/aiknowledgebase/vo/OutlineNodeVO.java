package com.yansheng.aiknowledgebase.vo;

import lombok.Data;

/** Outline 节点(不含正文,用于标题树导航) */
@Data
public class OutlineNodeVO {
    private Long id;
    private Long parentId;
    private Integer nodeIndex;
    private Integer level;
    private String title;
    private String headingPath;
    private Integer sourceStartOffset;
    private Integer sourceEndOffset;
    /** 该节点关联的真实切片数;空父标题节点为 0(B 方案) */
    private Integer sourceChunkCount;
}