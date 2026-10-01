package com.yansheng.aiknowledgebase.controller;

import com.yansheng.aiknowledgebase.common.Result;
import com.yansheng.aiknowledgebase.service.OutlineIndexService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * B-114 Outline 导航层(Phase1 后端最小闭环)。
 * 只读浏览标题树与 source_chunks 溯源;rebuild 为作者本人的存量补偿入口。
 * 权限口径与 FileController 一致:服务层校验 knowledge.author == 当前登录用户名。
 */
@RestController
@RequestMapping("/api/outline")
public class OutlineController {

    private final OutlineIndexService outlineIndexService;

    public OutlineController(OutlineIndexService outlineIndexService) {
        this.outlineIndexService = outlineIndexService;
    }

    /** 某文件的标题树(先序平坦列表 + 各节点关联切片数) */
    @GetMapping("/file/{fileId}")
    public Result getTree(@PathVariable Long fileId) {
        return Result.success(outlineIndexService.getTree(fileId));
    }

    /** 节点详情 + source_chunks 溯源(只指向真实切片) */
    @GetMapping("/node/{nodeId}")
    public Result getNodeDetail(@PathVariable Long nodeId) {
        return Result.success(outlineIndexService.getNodeDetail(nodeId));
    }

    /** 补偿入口:用 OSS 原文为存量 md 文件重建导航层,返回节点数 */
    @PostMapping("/file/{fileId}/rebuild")
    public Result rebuild(@PathVariable Long fileId) {
        return Result.success(outlineIndexService.rebuild(fileId));
    }
}