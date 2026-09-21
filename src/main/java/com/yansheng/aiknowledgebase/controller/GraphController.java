package com.yansheng.aiknowledgebase.controller;

import com.yansheng.aiknowledgebase.common.Result;
import com.yansheng.aiknowledgebase.service.GraphService;
import com.yansheng.aiknowledgebase.vo.GraphVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    /**
     * 知识图谱:节点=条目+文件,边=结构边(归属)+相似边(文件级 embedding 余弦 top2)。
     * JWT 过滤器全局拦截,UserContext 已注入当前用户,服务内只返回本人数据。
     */
    @GetMapping("/graph")
    public Result<GraphVO> getGraph() {
        return Result.success(graphService.getGraph());
    }
}
