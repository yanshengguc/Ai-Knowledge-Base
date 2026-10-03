package com.yansheng.aiknowledgebase.service.impl;


import com.aliyun.dashvector.DashVectorClient;
import com.aliyun.dashvector.DashVectorCollection;
import com.aliyun.dashvector.models.Doc;
import com.aliyun.dashvector.models.DocOpResult;
import com.aliyun.dashvector.models.Vector;
import com.aliyun.dashvector.models.requests.DeleteDocRequest;
import com.aliyun.dashvector.models.requests.FetchDocRequest;
import com.aliyun.dashvector.models.requests.UpsertDocRequest;
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

    private DashVectorCollection requireCollection() {
        if (collection == null) {
            throw new IllegalStateException("向量库不可用，当前已降级为 BM25 检索");
        }
        return collection;
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

        Response<List<DocOpResult>> response = requireCollection()
                .upsert(UpsertDocRequest.builder().doc(doc).build());

        if (!response.isSuccess()) {
            throw new RuntimeException("向量插入失败: " + response.getMessage());
        }
        checkDocOpResults(response, "向量插入");
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

        Response<List<DocOpResult>> response = requireCollection()
                .upsert(UpsertDocRequest.builder().docs(docs).build());

        if (!response.isSuccess()) {
            throw new RuntimeException("向量批量插入失败: " + response.getMessage());
        }
        checkDocOpResults(response, "向量批量插入");
    }

    /**
     * 逐条校验 DashVector 写入结果:
     * 顶层 isSuccess() 为 true 时逐条仍可能失败(如 -2027 Duplicate Key),
     * 只看顶层会导致失败被静默吞掉。任一 code != 0 即抛异常。
     */
    private void checkDocOpResults(Response<List<DocOpResult>> response, String opName) {
        List<DocOpResult> results = response.getOutput();
        if (results == null) {
            return;
        }
        int failed = 0;
        DocOpResult first = null;
        for (DocOpResult r : results) {
            if (r.getCode() != 0) {
                failed++;
                if (first == null) {
                    first = r;
                }
            }
        }
        if (failed > 0) {
            throw new RuntimeException(String.format(
                    "%s 部分失败: 失败条数=%d, 首个失败 id=%s, code=%d, message=%s, requestId=%s",
                    opName, failed, first.getId(), first.getCode(), first.getMessage(), response.getRequestId()));
        }
    }

    @Override
    public int countExisting(List<Long> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return 0;
        }
        DashVectorCollection col = requireCollection();
        // fetch 单次 ids 有上限(1024),分批回查;只统计真正返回的文档
        final int fetchBatch = 100;
        int found = 0;
        for (int from = 0; from < chunkIds.size(); from += fetchBatch) {
            int to = Math.min(from + fetchBatch, chunkIds.size());
            List<String> ids = new ArrayList<>(to - from);
            for (int i = from; i < to; i++) {
                ids.add(String.valueOf(chunkIds.get(i)));
            }
            Response<Map<String, Doc>> resp = col.fetch(
                    FetchDocRequest.builder().ids(ids).build());
            if (!resp.isSuccess()) {
                throw new RuntimeException("向量回查失败: code=" + resp.getCode()
                        + ", message=" + resp.getMessage());
            }
            Map<String, Doc> output = resp.getOutput();
            if (output != null) {
                for (Map.Entry<String, Doc> entry : output.entrySet()) {
                    if (entry.getValue() != null) {
                        found++;
                    }
                }
            }
        }
        return found;
    }

    @Override
    public void deleteByFileId(Long fileId) {
        if (fileId == null) {
            return;
        }
        if (collection == null) {
            log.warn("跳过向量清理:向量库当前不可用, fileId={}", fileId);
            return;
        }
        try {
            // 1. 查出该文件的所有向量主键(chunkId):
            //    DashVector query 必须带向量,用零向量 + filter 只取该文件范围(主键已足够)
            //    单次 topk 有上限,改为分页循环,避免单文件 chunk 数 > 页大小时残留孤儿向量
            int dim = requireCollection().getCollectionMeta().getDimension();
            List<Float> zeroVector = new ArrayList<>(dim);
            for (int i = 0; i < dim; i++) {
                zeroVector.add(0f);
            }

            // 分页参数:每轮查 pageSize 条主键并删除,累计删除不超过 maxTotal
            final int pageSize = 100;
            final int maxRounds = 50;
            final int maxTotal = 5000;
            int totalDeleted = 0;
            int round = 0;
            while (true) {
                if (round >= maxRounds || totalDeleted >= maxTotal) {
                    // 安全上限:防止删除未生效/异常情况下无限循环,触顶时可能仍有残留
                    log.warn("向量清理达到安全上限,可能仍有残留, fileId={}, round={}, 累计删除={}",
                            fileId, round, totalDeleted);
                    return;
                }
                round++;

                // 每轮重新查询下一页主键(已删除的不会再返回)
                Response<List<Doc>> queryResp = collection.query(QueryDocRequest.builder()
                        .vector(Vector.builder().value(zeroVector).build())
                        .topk(pageSize)
                        .filter("file_id = " + fileId)
                        .build());
                if (!queryResp.isSuccess()) {
                    // 查询失败:可观测性问题,与"确实无向量"区分开(8/23 评估时发现 fileId=50 删除查不到)
                    log.warn("向量清理查询失败(可能残留), fileId={}, 累计删除={}, message={}",
                            fileId, totalDeleted, queryResp.getMessage());
                    return;
                }
                List<Doc> docs = queryResp.getOutput();
                if (docs == null || docs.isEmpty()) {
                    // 查不到即清理完成(区分"本就无向量"与"清理已完成")
                    if (totalDeleted == 0) {
                        log.info("向量清理:该文件无向量(正常), fileId={}", fileId);
                    } else {
                        log.info("已清理向量, fileId={}, count={}", fileId, totalDeleted);
                    }
                    return;
                }
                List<String> ids = new ArrayList<>(docs.size());
                for (Doc doc : docs) {
                    ids.add(doc.getId());
                }

                // 2. 按主键删除(DashVector delete 只支持按主键 ids,不支持 filter)
                Response<List<DocOpResult>> delResp = collection.delete(
                        DeleteDocRequest.builder().ids(ids).build());
                if (!delResp.isSuccess()) {
                    log.warn("向量删除失败(不影响主流程), fileId={}, ids={}, 累计删除={}, message={}",
                            fileId, ids.size(), totalDeleted, delResp.getMessage());
                    return;
                }
                totalDeleted += ids.size();
                log.info("向量清理已删除一批, fileId={}, 本批={}, 累计删除={}", fileId, ids.size(), totalDeleted);

                if (ids.size() < pageSize) {
                    // 本页未取满说明已到末页,清理完成
                    log.info("已清理向量, fileId={}, count={}", fileId, totalDeleted);
                    return;
                }
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
        Response<List<Doc>> response = requireCollection().query(request);

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