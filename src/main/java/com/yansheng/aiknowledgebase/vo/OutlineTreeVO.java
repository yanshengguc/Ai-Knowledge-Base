package com.yansheng.aiknowledgebase.vo;

import lombok.Data;

import java.util.List;

/** Outline 标题树(先序平坦列表,前端按 parentId 组装树) */
@Data
public class OutlineTreeVO {
    private Long fileId;
    private String fileName;
    private Integer nodeCount;
    private List<OutlineNodeVO> nodes;
}