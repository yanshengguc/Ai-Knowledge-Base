package com.yansheng.aiknowledgebase.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** B-114 Outline 导航层节点:只描述标题层级与原文偏移,不改写 knowledge_chunk */
@Setter
@Getter
public class OutlineNodeEntity {

    private Long id;

    private Long fileId;

    /** 父节点 id,根节点为 null */
    private Long parentId;

    /** 文件内先序序号,从 0 开始 */
    private Integer nodeIndex;

    /** 标题层级 1-6 */
    private Integer level;

    private String title;

    /** 完整标题路径,以 " / " 分隔 */
    private String headingPath;

    /** 标题行起始 UTF-16 下标 */
    private Integer sourceStartOffset;

    /** 标题行结束 UTF-16 下标(不含换行) */
    private Integer sourceEndOffset;

    private LocalDateTime createTime;
}