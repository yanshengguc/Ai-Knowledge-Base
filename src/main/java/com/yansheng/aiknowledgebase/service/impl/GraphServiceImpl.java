package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.EmbeddingService;
import com.yansheng.aiknowledgebase.service.GraphService;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.GraphVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识图谱实现(B-107 L3)。
 *
 * 设计决策(为什么不查 DashVector 里现成的 chunk 向量):
 * 1. 共引边(chat references 落库)依赖 B-110,未做,当前唯一可用的真实边来源是文件级语义相似;
 * 2. DashVector 的存量向量是 chunk 级且只支持近邻查询,拿不到"全量向量"做两两相似;
 * 3. 文件级向量用"文件名+首切片"现场 embedding(一次 embedBatch,65 文件毫秒级),
 *    两两余弦在内存算 O(n^2/2),n 为百级完全可接受——Obsidian 的"相关笔记"本质也是局部相似。
 * 降级:embedding 调用失败时只返回结构边,不阻断整个图(与 Redis 降级直查 DB 同一哲学)。
 */
@Slf4j
@Service
public class GraphServiceImpl implements GraphService {

    /** 相似边阈值:低于该余弦值的关联不建边(过滤"只是都提到 Java"这类弱相关) */
    static final double SIM_THRESHOLD = 0.45;
    /** 每个文件保留 top-K 最相似邻居(Obsidian 相关笔记通常也就三五条) */
    static final int TOP_K_NEIGHBORS = 2;
    /** 参与 embedding 的首切片截断长度,控 token 成本 */
    private static final int EMBED_TEXT_MAX_LEN = 500;
    /**
     * embedding 分批大小。text-embedding-v3 实测单请求上限 10 条
     * (9/21 线上:65 条报 "input texts limit 25"(网关层),20 条报
     * "batch size is invalid, it should not be larger than 10"(算法层)),留余量取 8。
     */
    static final int EMBED_BATCH_SIZE = 8;

    private final KnowledgeMapper knowledgeMapper;
    private final FileMapper fileMapper;
    private final ChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;

    public GraphServiceImpl(KnowledgeMapper knowledgeMapper, FileMapper fileMapper,
                            ChunkMapper chunkMapper, EmbeddingService embeddingService) {
        this.knowledgeMapper = knowledgeMapper;
        this.fileMapper = fileMapper;
        this.chunkMapper = chunkMapper;
        this.embeddingService = embeddingService;
    }

    @Override
    public GraphVO getGraph() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("未登录");
        }

        List<GraphVO.NodeVO> nodes = new ArrayList<>();
        List<GraphVO.EdgeVO> edges = new ArrayList<>();
        List<FileEntity> allFiles = new ArrayList<>();

        List<KnowledgeEntity> knowledges = knowledgeMapper.selectByUserId(userId);
        for (KnowledgeEntity k : knowledges) {
            GraphVO.NodeVO kn = new GraphVO.NodeVO();
            kn.setId("k-" + k.getId());
            kn.setType("knowledge");
            kn.setName(k.getTitle());
            kn.setGroup(k.getId());
            kn.setCategory(k.getCategory());
            nodes.add(kn);

            List<FileEntity> files = fileMapper.selectFileByKnowledgeId(k.getId());
            for (FileEntity f : files) {
                GraphVO.NodeVO fn = new GraphVO.NodeVO();
                fn.setId("f-" + f.getId());
                fn.setType("file");
                fn.setName(f.getFileName());
                fn.setGroup(k.getId());
                fn.setStatus(f.getStatus());
                fn.setFileType(f.getFileType());
                nodes.add(fn);
                allFiles.add(f);

                GraphVO.EdgeVO edge = new GraphVO.EdgeVO();
                edge.setSource(kn.getId());
                edge.setTarget(fn.getId());
                edge.setType("structure");
                edge.setWeight(1.0);
                edges.add(edge);
            }
        }

        edges.addAll(buildSimilarEdges(allFiles));

        GraphVO vo = new GraphVO();
        vo.setNodes(nodes);
        vo.setEdges(edges);
        return vo;
    }

    /**
     * 相似边:文件级 embedding 余弦相似,每个文件取 top-K 且过阈值,对去重(无向边只出一次)。
     * embedding 失败降级为空列表(图仍可渲染,只是少了相似边)。
     */
    private List<GraphVO.EdgeVO> buildSimilarEdges(List<FileEntity> files) {
        if (files.size() < 2) {
            return List.of();
        }
        List<String> texts = new ArrayList<>(files.size());
        for (FileEntity f : files) {
            texts.add(buildEmbedText(f));
        }

        List<float[]> vectors = new ArrayList<>(files.size());
        try {
            // 分批调用:DashScope 单请求上限 25 条,超限整包被拒(9/21 线上实测)
            for (int start = 0; start < texts.size(); start += EMBED_BATCH_SIZE) {
                List<String> batch = texts.subList(start, Math.min(start + EMBED_BATCH_SIZE, texts.size()));
                vectors.addAll(embeddingService.embedBatch(batch));
            }
        } catch (Exception e) {
            log.warn("知识图谱相似边 embedding 失败,降级为仅结构边: {}", e.getMessage());
            return List.of();
        }
        if (vectors == null || vectors.size() != files.size()) {
            log.warn("知识图谱 embedding 返回数量不符: files={}, vectors={}",
                    files.size(), vectors == null ? 0 : vectors.size());
            return List.of();
        }

        // 每个 i 的 top-K 邻居(i<j 的 pairKey 去重,无向边只出一次)
        List<GraphVO.EdgeVO> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < files.size(); i++) {
            // 收集 i 与所有其他文件的相似度,过阈值后按相似度降序取 top-K
            Map<Integer, Double> simByIdx = new HashMap<>();
            for (int j = 0; j < files.size(); j++) {
                if (i != j) {
                    double sim = cosine(vectors.get(i), vectors.get(j));
                    if (sim >= SIM_THRESHOLD) {
                        simByIdx.put(j, sim);
                    }
                }
            }
            List<Integer> top = simByIdx.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                    .limit(TOP_K_NEIGHBORS)
                    .map(Map.Entry::getKey)
                    .toList();
            for (int j : top) {
                String key = i < j ? i + ":" + j : j + ":" + i;
                if (!seen.add(key)) {
                    continue;
                }
                GraphVO.EdgeVO edge = new GraphVO.EdgeVO();
                edge.setSource("f-" + files.get(i).getId());
                edge.setTarget("f-" + files.get(j).getId());
                edge.setType("similar");
                edge.setWeight(simByIdx.get(j));
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * 文件级 embedding 文本 = 文件名 + 首切片(按 chunkIndex 取最早一段,截断控成本)。
     * 首切片比文件名有区分度得多(同名/相似名文件也能靠内容分开)。
     */
    private String buildEmbedText(FileEntity f) {
        StringBuilder sb = new StringBuilder(f.getFileName() == null ? "" : f.getFileName());
        List<ChunkEntity> chunks = chunkMapper.selectByFileId(f.getId());
        if (chunks != null && !chunks.isEmpty()) {
            String first = chunks.stream()
                    .min(Comparator.comparingInt(ChunkEntity::getChunkIndex))
                    .map(ChunkEntity::getContent)
                    .orElse("");
            if (first != null && first.length() > EMBED_TEXT_MAX_LEN) {
                first = first.substring(0, EMBED_TEXT_MAX_LEN);
            }
            if (first != null && !first.isBlank()) {
                sb.append('\n').append(first);
            }
        }
        String text = sb.toString().trim();
        // embedding 要求非空:极端情况(无名无切片)退化为 id 占位
        return text.isEmpty() ? String.valueOf(f.getId()) : text;
    }

    /** 余弦相似度;零向量返回 0(不参与建边)。public 供单测直接验证数学正确性(维度无关) */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
