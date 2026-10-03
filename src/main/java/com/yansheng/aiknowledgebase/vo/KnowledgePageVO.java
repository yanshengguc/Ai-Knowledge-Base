package com.yansheng.aiknowledgebase.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** B-104 知识列表服务端分页外壳(含关键词/分类过滤结果总数) */
@Setter
@Getter
public class KnowledgePageVO {

    private long total;

    private int page;

    private int size;

    private List<KnowledgeVO> list;
}
