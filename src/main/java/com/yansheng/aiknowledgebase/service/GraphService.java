package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.vo.GraphVO;

public interface GraphService {
    /**
     * 构建当前用户的知识图谱:节点(条目+文件) + 两类边。
     * 只含当前用户自己的数据(与知识列表同口径的越权防护)。
     */
    GraphVO getGraph();
}
