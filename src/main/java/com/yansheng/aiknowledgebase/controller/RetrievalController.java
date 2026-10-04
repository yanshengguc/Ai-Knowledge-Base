package com.yansheng.aiknowledgebase.controller;

import com.yansheng.aiknowledgebase.common.Result;
import com.yansheng.aiknowledgebase.dto.RetrievalLocateDTO;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.service.RetrievalService;
import com.yansheng.aiknowledgebase.vo.ChunkHitVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * B-110 知识跳转定位:把「选中文本」当 query,复用现有混合检索返回真实命中的 chunk。
 * 只读端点,零算法改动、零 DDL;用户隔离沿用 RetrievalService.retrieveTopK 内部口径。
 * query 空/超长护栏由 DTO 的 jakarta 注解在 Web 层前移拦截(B-127 约定)。
 */
@RestController
@RequestMapping("/api/retrieval")
public class RetrievalController {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 10;

    private final RetrievalService retrievalService;

    public RetrievalController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping("/locate")
    public Result<List<ChunkHitVO>> locate(@Valid @RequestBody RetrievalLocateDTO dto) {
        int topK = normalizeTopK(dto.getTopK());
        List<SearchResult> hits = retrievalService.retrieveTopK(dto.getQuery().trim());
        List<ChunkHitVO> vos = new ArrayList<>();
        if (hits != null) {
            int limit = Math.min(topK, hits.size());
            for (int i = 0; i < limit; i++) {
                SearchResult hit = hits.get(i);
                ChunkHitVO vo = new ChunkHitVO();
                vo.setFileId(hit.getFileId());
                vo.setFileName(hit.getFileName());
                vo.setChunkIndex(hit.getChunkIndex());
                vo.setContent(hit.getContent());
                vo.setScore(hit.getScore());
                vos.add(vo);
            }
        }
        return Result.success(vos);
    }

    /** null→默认 5;非空收敛到 [1,10] */
    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        if (topK < 1) {
            return 1;
        }
        return Math.min(topK, MAX_TOP_K);
    }
}
