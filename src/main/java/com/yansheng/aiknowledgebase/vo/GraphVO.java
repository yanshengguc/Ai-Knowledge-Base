package com.yansheng.aiknowledgebase.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 知识图谱 VO(GET /api/graph)。
 * 节点 = 知识条目 + 文件/笔记;边 = 结构边(条目→文件归属) + 相似边(文件级 embedding 余弦 top2)。
 * 前端用 ECharts 力导向布局渲染(Obsidian 相关笔记风格)。
 */
@Getter
@Setter
public class GraphVO {
    private List<NodeVO> nodes;
    private List<EdgeVO> edges;

    @Getter
    @Setter
    public static class NodeVO {
        /** "k-{知识id}" / "f-{文件id}",避免两类节点 id 撞号 */
        private String id;
        /** knowledge | file */
        private String type;
        private String name;
        /** 所属知识条目 id(前端按 group 配色,Obsidian 文件夹分色) */
        private Long group;
        private String status;
        private String fileType;
        private String category;
    }

    @Getter
    @Setter
    public static class EdgeVO {
        private String source;
        private String target;
        /** structure(归属) | similar(向量相似) */
        private String type;
        /** similar 边 = 余弦相似度(0~1);structure 边恒为 1.0 */
        private double weight;
    }
}
