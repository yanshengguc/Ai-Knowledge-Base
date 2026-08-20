package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.entity.SearchResult;

import java.util.List;

/**
 * 重排服务(Rerank):对粗召回结果做精排。
 *
 * 面试讲法:
 *   1. RAG 完整流程 = 检索(粗排)→ 重排(精排)→ 上下文构造 —— JD 明确要求含重排
 *   2. 粗排(向量检索)召回 TopK*N,可能混入不相关;重排用交叉编码器对"查询-文档对"
 *      重新打分,更精准 —— 这是"从搜得到跨越到推得出"的关键一步
 *   3. 实现:博查 Semantic Reranker API(gte-rerank),失败降级返回原顺序(容错)
 */
public interface RerankService {

    /**
     * 对候选结果重排
     * @param query      原始查询
     * @param candidates 粗召回候选(按原分数排序)
     * @param topN       返回前 N 个
     * @return 重排后的结果;失败降级返回原顺序
     */
    List<SearchResult> rerank(String query, List<SearchResult> candidates, int topN);
}
