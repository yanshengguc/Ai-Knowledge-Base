package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.vo.OutlineNodeDetailVO;
import com.yansheng.aiknowledgebase.vo.OutlineTreeVO;

/**
 * B-114 Outline 导航层(Phase1)。
 *
 * 定位:只做标题层级导航与 source_chunks 溯源,不回填不改写 knowledge_chunk、
 * 不参与检索路由(Phase2 才挂索引)。空父标题不生成空 Chunk,也不产生空关联。
 */
public interface OutlineIndexService {

    /** 为某文件生成/重建导航层(幂等:按 file_id 先删后插),返回写入的节点数 */
    int indexFile(Long fileId, String markdown);

    /** 补偿入口:用 OSS 原文为存量 md 文件重建导航层(作者本人;存量固定窗口批次可能大面积降级为空关联) */
    int rebuild(Long fileId);

    /** 标题树(先序平坦列表 + 各节点关联切片数) */
    OutlineTreeVO getTree(Long fileId);

    /** 节点详情 + source_chunks 溯源 */
    OutlineNodeDetailVO getNodeDetail(Long nodeId);
}