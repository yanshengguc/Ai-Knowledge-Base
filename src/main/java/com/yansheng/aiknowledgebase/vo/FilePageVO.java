package com.yansheng.aiknowledgebase.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** B-104 文件列表服务端分页外壳(按知识 id 分页) */
@Setter
@Getter
public class FilePageVO {

    private long total;

    private int page;

    private int size;

    private List<FileVO> list;
}
