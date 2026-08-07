package com.yansheng.aiknowledgebase.service.impl;


import com.aliyun.dashvector.DashVectorClient;
import com.aliyun.dashvector.DashVectorCollection;
import com.aliyun.dashvector.models.Doc;
import com.aliyun.dashvector.models.DocOpResult;
import com.aliyun.dashvector.models.Vector;
import com.aliyun.dashvector.models.requests.InsertDocRequest;
import com.aliyun.dashvector.models.responses.Response;
import com.yansheng.aiknowledgebase.service.VectorStoreService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class VectorStoreServiceImpl implements VectorStoreService {

    @Value("${dashvector.api-key}")
    private String apiKey;

    @Value("${dashvector.endpoint}")
    private String endpoint;

    private static final String COLLECTION_NAME = "knowledge_chunk_vector";

    private DashVectorClient client;
    private DashVectorCollection collection;

    @PostConstruct
    public void init() {
        client = new DashVectorClient(apiKey, endpoint);
        collection = client.get(COLLECTION_NAME);
    }

    @Override
    public void insert(Long chunkId, Long documentId, String content, float[] vector) {
        if (chunkId == null) {
            throw new IllegalArgumentException("chunkId不能为空");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId不能为空");
        }
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("向量不能为空");
        }

        List<Float> vectorList = new ArrayList<>();
        for (float v : vector) {
            vectorList.add(v);
        }

        Doc doc = Doc.builder()
                .id(String.valueOf(chunkId))  // DashVector的id字段本身是String类型,这里要转
                .vector(Vector.builder().value(vectorList).build())
                .field("document_id", documentId)
                .field("content", content)
                .build();

        Response<List<DocOpResult>> response = collection.insert(InsertDocRequest.builder().doc(doc).build());

        if (!response.isSuccess()) {
            throw new RuntimeException("向量插入失败: " + response.getMessage());
        }
    }
}