package com.yansheng.aiknowledgebase.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 管理端用户列表分页外壳(服务端分页,避免全量拉取) */
@Setter
@Getter
public class AdminUserPageVO {

    private long total;

    private int page;

    private int size;

    private List<AdminUserVO> list;
}