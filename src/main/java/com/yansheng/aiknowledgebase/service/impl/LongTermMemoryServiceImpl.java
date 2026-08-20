package com.yansheng.aiknowledgebase.service.impl;

import com.aliyun.dashvector.DashVectorClient;
import com.aliyun.dashvector.DashVectorCollection;
import com.aliyun.dashvector.common.DashVectorException;
import com.aliyun.dashvector.models.CollectionMeta;
import com.aliyun.dashvector.models.Doc;
import com.aliyun.dashvector.models.DocOpResult;
import com.aliyun.dashvector.models.Vector;
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
 * 降级:任何异常不影响主流程(写入失败丢弃、召回失败返回空)。
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

    private final EmbeddingService embeddingService;

    private DashVectorClient client;
    private DashVectorCollection collection;

    public LongTermMemoryServiceImpl(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    public void init() throws DashVectorException {
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
    }

    @Override
    public void remember(Long userId, String content) {
        if (userId == null || content == null || content.isBlank()) {
            return;
        }
        try {
            float[] vector = embeddingService.embed(content);
            List<Float> vectorList = new ArrayList<>();
            for (float v : vector) {
                vectorList.add(v);
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
