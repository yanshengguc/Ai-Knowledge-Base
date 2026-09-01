package com.yansheng.aiknowledgebase.service.impl;

import com.aliyun.dashvector.DashVectorClient;
import com.aliyun.dashvector.DashVectorCollection;
import com.aliyun.dashvector.models.CollectionMeta;
import com.aliyun.dashvector.models.Doc;
import com.aliyun.dashvector.models.DocOpResult;
import com.aliyun.dashvector.models.Vector;
import com.aliyun.dashvector.models.requests.DeleteDocRequest;
import com.aliyun.dashvector.models.requests.InsertDocRequest;
import com.aliyun.dashvector.models.requests.QueryDocRequest;
import com.aliyun.dashvector.models.responses.Response;
import com.yansheng.aiknowledgebase.service.EmbeddingService;
import com.yansheng.aiknowledgebase.service.LongTermMemoryService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 长期记忆:向量库持久化(跨会话)。
 * 集合 long_term_memory,按 user_id 字段隔离,语义召回。
 * 治理(remember 写入前单点执行,一次向量查询同时完成):
 *  - 语义去重:最相似记忆 score >= 阈值 → 跳过写入
 *  - 过期清理:created_at 超过保留天数 → 批量删除
 *  - 容量上限:该用户记忆已满 → 滚动淘汰最旧一条(总数稳定在上限)
 *  - 摘要压缩:单条记忆超长 → 存储层兜底截断
 * 降级:任何异常不影响主流程(写入失败丢弃、召回失败返回空、治理失败照常写入)。
 */
@Slf4j
@Service
public class LongTermMemoryServiceImpl implements LongTermMemoryService {

    private static final String COLLECTION_NAME = "long_term_memory";
    /** 知识库向量集合(用于对齐向量维度) */
    private static final String SOURCE_COLLECTION = "knowledge_chunk_vector";
    private static final int DEFAULT_DIMENSION = 1536;

    @Value("${dashvector.api-key}")
    private String apiKey;

    @Value("${dashvector.endpoint}")
    private String endpoint;

    @Value("${memory.governance.enabled:true}")
    private boolean governanceEnabled;

    @Value("${memory.governance.dedup-threshold:0.92}")
    private float dedupThreshold;

    @Value("${memory.governance.max-per-user:500}")
    private int maxPerUser;

    @Value("${memory.governance.retention-days:180}")
    private int retentionDays;

    @Value("${memory.governance.content-max-length:200}")
    private int contentMaxLength;

    private final EmbeddingService embeddingService;

    private DashVectorClient client;
    private DashVectorCollection collection;

    public LongTermMemoryServiceImpl(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    public void init() {
        try {
            client = new DashVectorClient(apiKey, endpoint);
            Response<CollectionMeta> meta = client.describe(COLLECTION_NAME);
            if (!meta.isSuccess()) {
                // 集合不存在 → 按知识库集合维度创建
                int dimension = DEFAULT_DIMENSION;
                Response<CollectionMeta> src = client.describe(SOURCE_COLLECTION);
                if (src.isSuccess() && src.getOutput() != null) {
                    dimension = src.getOutput().getDimension();
                }
                client.create(COLLECTION_NAME, dimension);
                log.info("长期记忆集合已创建: {}, dimension={}", COLLECTION_NAME, dimension);
            }
            collection = client.get(COLLECTION_NAME);
        } catch (Exception e) {
            // 供应商不可用时不阻断启动,记忆读写已有各自的 try/catch 降级
            log.error("长期记忆向量库初始化失败(降级启动,记忆功能停用): {}", e.getMessage());
        }
    }

    @Override
    public void remember(Long userId, String content) {
        if (userId == null || content == null || content.isBlank()) {
            return;
        }
        // 摘要压缩:存储层兜底截断,无论调用方传多长,单条记忆不超上限
        if (content.length() > contentMaxLength) {
            content = content.substring(0, contentMaxLength);
        }
        try {
            float[] vector = embeddingService.embed(content);
            List<Float> vectorList = new ArrayList<>();
            for (float v : vector) {
                vectorList.add(v);
            }
            // 写入前治理(去重/过期/容量),复用同一向量不多花一次 embedding
            if (governanceEnabled && governBeforeWrite(userId, vectorList)) {
                log.debug("长期记忆去重命中:userId={}, 跳过写入", userId);
                return;
            }
            String id = userId + "_" + System.currentTimeMillis();
            Doc doc = Doc.builder()
                    .id(id)
                    .vector(Vector.builder().value(vectorList).build())
                    .field("user_id", userId)
                    .field("content", content)
                    .field("created_at", System.currentTimeMillis())
                    .build();
            Response<List<DocOpResult>> resp =
                    collection.insert(InsertDocRequest.builder().doc(doc).build());
            if (!resp.isSuccess()) {
                log.warn("长期记忆写入失败: code={}, message={}", resp.getCode(), resp.getMessage());
            }
        } catch (Exception e) {
            // 降级:记忆写入失败不影响对话主流程
            log.warn("长期记忆写入异常(降级): {}", e.getMessage());
        }
    }

    /**
     * 写入前治理:一次向量查询(topK=容量上限,filter 该用户)同时完成三项检查。
     * topK=上限的正确性:返回条数 < 上限 → 未满不淘汰;返回条数 >= 上限 → 已满,
     * 淘汰最旧 1 条再写入,总数稳定在上限。
     *
     * @return true 表示与已有记忆高度重复,应跳过写入
     */
    private boolean governBeforeWrite(Long userId, List<Float> vectorList) {
        try {
            QueryDocRequest request = QueryDocRequest.builder()
                    .vector(Vector.builder().value(vectorList).build())
                    .topk(maxPerUser)
                    .filter("user_id = " + userId)
                    .outputField("created_at")
                    .build();
            Response<List<Doc>> resp = collection.query(request);
            if (!resp.isSuccess() || resp.getOutput() == null || resp.getOutput().isEmpty()) {
                return false;
            }
            List<Doc> hits = resp.getOutput();

            // 1) 语义去重:与已有记忆最相似的一条超过阈值 → 跳过写入
            //    DashVector cosine 度量下 score 是距离(1-相似度,越小越相似)
            float minDistance = Float.MAX_VALUE;
            for (Doc hit : hits) {
                minDistance = Math.min(minDistance, hit.getScore());
            }
            if (minDistance <= 1f - dedupThreshold) {
                return true;
            }

            // 2) 过期清理 + 3) 容量淘汰:收集待删 id(过期优先,未过期的最旧一条作为容量淘汰候选)
            long expiryBefore = System.currentTimeMillis() - retentionDays * 24L * 3600L * 1000L;
            List<String> toDelete = new ArrayList<>();
            String oldestId = null;
            long oldestTs = Long.MAX_VALUE;
            for (Doc hit : hits) {
                long ts = parseCreatedAt(hit);
                if (ts < expiryBefore) {
                    toDelete.add(hit.getId());
                } else if (ts < oldestTs) {
                    oldestTs = ts;
                    oldestId = hit.getId();
                }
            }
            int expired = toDelete.size();
            if (hits.size() >= maxPerUser && oldestId != null) {
                toDelete.add(oldestId);
            }
            if (!toDelete.isEmpty()) {
                collection.delete(DeleteDocRequest.builder().ids(toDelete).build());
                log.info("长期记忆治理:userId={}, 检出={}条, 过期删除={}条, 容量淘汰={}",
                        userId, hits.size(), expired, toDelete.size() - expired);
            }
            return false;
        } catch (Exception e) {
            // 治理失败不阻塞写入:宁可多存,不可丢记忆
            log.warn("长期记忆治理异常(跳过治理,继续写入): {}", e.getMessage());
            return false;
        }
    }

    private long parseCreatedAt(Doc doc) {
        Map<String, Object> fields = doc.getFields();
        if (fields == null || fields.get("created_at") == null) {
            return System.currentTimeMillis();
        }
        try {
            return Long.parseLong(String.valueOf(fields.get("created_at")).replace(".0", ""));
        } catch (NumberFormatException e) {
            return System.currentTimeMillis();
        }
    }

    @Override
    public List<String> recall(Long userId, String query, int topK) {
        if (userId == null || query == null || query.isBlank()) {
            return new ArrayList<>();
        }
        try {
            float[] queryVector = embeddingService.embed(query);
            List<Float> vectorList = new ArrayList<>();
            for (float v : queryVector) {
                vectorList.add(v);
            }
            QueryDocRequest request = QueryDocRequest.builder()
                    .vector(Vector.builder().value(vectorList).build())
                    .topk(topK)
                    .filter("user_id = " + userId)
                    .outputField("content")
                    .build();
            Response<List<Doc>> resp = collection.query(request);
            if (!resp.isSuccess()) {
                log.warn("长期记忆检索失败: code={}, message={}", resp.getCode(), resp.getMessage());
                return new ArrayList<>();
            }
            List<String> memories = new ArrayList<>();
            for (Doc doc : resp.getOutput()) {
                Map<String, Object> fields = doc.getFields();
                if (fields != null && fields.get("content") != null) {
                    memories.add(String.valueOf(fields.get("content")));
                }
            }
            log.info("长期记忆召回: userId={}, query={}, 命中={}条", userId, query, memories.size());
            return memories;
        } catch (Exception e) {
            // 降级:召回失败返回空,不中断检索
            log.warn("长期记忆检索异常(降级): {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
