package com.yansheng.aiknowledgebase.service.impl;


import com.aliyun.dashvector.DashVectorClient;
import com.aliyun.dashvector.DashVectorCollection;
import com.aliyun.dashvector.models.Doc;
import com.aliyun.dashvector.models.DocOpResult;
import com.aliyun.dashvector.models.Vector;
import com.aliyun.dashvector.models.requests.DeleteDocRequest;
import com.aliyun.dashvector.models.requests.InsertDocRequest;
import com.aliyun.dashvector.models.requests.QueryDocRequest;
import com.aliyun.dashvector.models.responses.Response;
import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.service.VectorStoreService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
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
        try {
            client = new DashVectorClient(apiKey, endpoint);
            collection = client.get(COLLECTION_NAME);
        } catch (Exception e) {
            // 供应商不可用时不阻断启动,检索层已兜底降级为 BM25 单路
            log.error("向量库初始化失败(降级启动,检索退化为 BM25): {}", e.getMessage());
        }
    }

    @Override
    public void insert(Long chunkId, Long fileId, String content, float[] vector) {
        if (chunkId == null) {
            throw new IllegalArgumentException("chunkId不能为空");
        }
        if (fileId == null) {
            throw new IllegalArgumentException("fileId不能为空");
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
                .field("file_id", fileId)
                .field("content", content)
                .build();

        Response<List<DocOpResult>> response = collection.insert(InsertDocRequest.builder().doc(doc).build());

        if (!response.isSuccess()) {
            throw new RuntimeException("向量插入失败: " + response.getMessage());
        }
    }

    @Override
    public void insertBatch(Long fileId, List<ChunkEntity> chunks, List<float[]> vectors) {
        if (fileId == null) {
            throw new IllegalArgumentException("fileId不能为空");
        }
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("chunks不能为空");
        }
        if (vectors == null || vectors.size() != chunks.size()) {
            throw new IllegalArgumentException("向量数与切片数不一致");
        }

        List<Doc> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            ChunkEntity chunk = chunks.get(i);
            float[] vector = vectors.get(i);
            List<Float> vectorList = new ArrayList<>(vector.length);
            for (float v : vector) {
                vectorList.add(v);
            }
            docs.add(Doc.builder()
                    .id(String.valueOf(chunk.getId()))
                    .vector(Vector.builder().value(vectorList).build())
                    .field("file_id", fileId)
                    .field("content", chunk.getContent())
                    .build());
        }

        Response<List<DocOpResult>> response = collection.insert(InsertDocRequest.builder().docs(docs).build());

        if (!response.isSuccess()) {
            throw new RuntimeException("向量批量插入失败: " + response.getMessage());
        }
    }

    @Override
    public void deleteByFileId(Long fileId) {
        if (fileId == null) {
            return;
        }
        try {
            // 1. 查出该文件的所有向量主键(chunkId):
            //    DashVector query 必须带向量,用零向量 + filter 只取该文件范围(主键已足够)
            int dim = collection.getCollectionMeta().getDimension();
            List<Float> zeroVector = new ArrayList<>(dim);
            for (int i = 0; i < dim; i++) {
                zeroVector.add(0f);
            }
            Response<List<Doc>> queryResp = collection.query(QueryDocRequest.builder()
                    .vector(Vector.builder().value(zeroVector).build())
                    .topk(100)
                    .filter("file_id = " + fileId)
                    .build());
            if (!queryResp.isSuccess()) {
                // 查询失败:可观测性问题,与"确实无向量"区分开(8/23 评估时发现 fileId=50 删除查不到)
                log.warn("向量清理查询失败(可能残留), fileId={}, message={}", fileId, queryResp.getMessage());
                return;
            }
            if (queryResp.getOutput() == null || queryResp.getOutput().isEmpty()) {
                log.info("向量清理:该文件无向量(正常), fileId={}", fileId);
                return;
            }
            List<String> ids = new ArrayList<>();
            for (Doc doc : queryResp.getOutput()) {
                ids.add(doc.getId());
            }

            // 2. 按主键删除(DashVector delete 只支持按主键 ids,不支持 filter)
            Response<List<DocOpResult>> delResp = collection.delete(
                    DeleteDocRequest.builder().ids(ids).build());
            if (!delResp.isSuccess()) {
                log.warn("向量删除失败(不影响主流程), fileId={}, ids={}, message={}",
                        fileId, ids.size(), delResp.getMessage());
            } else {
                log.info("已清理向量, fileId={}, count={}", fileId, ids.size());
            }
        } catch (Exception e) {
            // 向量删除失败不阻断业务删除(可后续重跑清理)
            log.warn("向量删除异常, fileId={}, error={}", fileId, e.getMessage());
        }
    }

    @Override
    public List<SearchResult> search(float[] vector, int topK) {
        return search(vector, topK, null);
    }

    @Override
    public List<SearchResult> search(float[] vector, int topK, String filter) {

        // 1. 参数校验
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("向量不能为空");
        }

        if (topK <= 0) {
            throw new IllegalArgumentException("topK需要大于0");
        }

        log.info("开始向量检索，vectorDimension={}, topK={}, filter={}",
                vector.length, topK, filter);

        // 2. float[] 转 List<Float>
        List<Float> vectorList = new ArrayList<>();

        for (float v : vector) {
            vectorList.add(v);
        }

        // 3. 构造查询向量
        Vector queryVector = Vector.builder()
                .value(vectorList)
                .build();

        // 4. 构造 Top-K 查询请求(可选 filter 表达式,按文件范围隔离)
        QueryDocRequest request;
        if (filter != null && !filter.isBlank()) {
            request = QueryDocRequest.builder()
                    .vector(queryVector)
                    .topk(topK)
                    .filter(filter)
                    .build();
        } else {
            request = QueryDocRequest.builder()
                    .vector(queryVector)
                    .topk(topK)
                    .build();
        }

        // 5. 调用 DashVector
        Response<List<Doc>> response = collection.query(request);

        // 6. 查询失败
        if (!response.isSuccess()) {

            log.error(
                    "DashVector查询失败，code={}, message={}, requestId={}, response={}",
                    response.getCode(),
                    response.getMessage(),
                    response.getRequestId(),
                    response
            );

            throw new RuntimeException(
                    "向量检索失败: code="
                            + response.getCode()
                            + ", message="
                            + response.getMessage()
                            + ", requestId="
                            + response.getRequestId()
            );
        }

        // 7. 获取查询结果
        List<Doc> docs = response.getOutput();

        if (docs == null || docs.isEmpty()) {
            log.info("DashVector查询成功，但没有检索到结果");
            return new ArrayList<>();
        }

        log.info("DashVector查询成功，返回{}条结果", docs.size());

        // 8. Doc → SearchResult
        List<SearchResult> results = new ArrayList<>();

        for (Doc doc : docs) {

            // DashVector id 是 String
            // 插入时使用的是 String.valueOf(chunkId)
            Long chunkId = Long.valueOf(doc.getId());

            // 获取字段
            Map<String, Object> fields = doc.getFields();

            if (fields == null) {
                log.warn("Doc字段为空，chunkId={}", chunkId);
                continue;
            }

            // file_id
            Object fileIdObject = fields.get("file_id");

            if (fileIdObject == null) {
                log.warn("Doc缺少file_id，chunkId={}", chunkId);
                continue;
            }

            Long fileId = ((Number) fileIdObject).longValue();

            // content
            Object contentObject = fields.get("content");

            if (contentObject == null) {
                log.warn("Doc缺少content，chunkId={}", chunkId);
                continue;
            }

            String content = contentObject.toString();

            // 相似度
            Double score = (double) doc.getScore();

            // 构造检索结果
            SearchResult result = new SearchResult(
                    fileId,
                    chunkId,
                    content,
                    score
            );

            results.add(result);
        }

        log.info("向量检索完成，最终返回{}条SearchResult", results.size());

        return results;
    }
}