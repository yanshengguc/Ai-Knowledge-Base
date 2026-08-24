package com.yansheng.aiknowledgebase;

import com.aliyun.dashvector.DashVectorClient;
import com.aliyun.dashvector.DashVectorCollection;
import com.aliyun.dashvector.models.Doc;
import com.aliyun.dashvector.models.Vector;
import com.aliyun.dashvector.models.requests.DeleteDocRequest;
import com.aliyun.dashvector.models.requests.InsertDocRequest;
import com.aliyun.dashvector.models.requests.QueryDocRequest;
import com.aliyun.dashvector.models.responses.Response;
import com.yansheng.aiknowledgebase.service.EmbeddingService;
import com.yansheng.aiknowledgebase.service.LongTermMemoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 长期记忆治理集成测试(真实 DashVector):
 *  - 语义去重:高度相似的记忆只保留一条
 *  - 容量上限:超出 max-per-user 后滚动淘汰最旧,总数稳定在上限
 *  - 过期清理:created_at 超过 retention-days 的记忆在下次写入时被删
 *  - 摘要压缩:超长记忆被截断到 content-max-length
 *
 * 测试参数收窄(cap=4/retention=30d/截断=50)让行为可快速观察;
 * DashVector 写删有索引可见性延迟,断言前轮询等待。
 */
@SpringBootTest
@Tag("e2e")
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "memory.governance.enabled=true",
        "memory.governance.dedup-threshold=0.90",
        "memory.governance.max-per-user=4",
        "memory.governance.retention-days=30",
        "memory.governance.content-max-length=50"
})
class LongTermMemoryGovernanceTest {

    private static final String COLLECTION = "long_term_memory";
    private static final long WAIT_MS = 10_000;

    @Autowired
    private LongTermMemoryService longTermMemoryService;
    @Autowired
    private EmbeddingService embeddingService;

    @Value("${dashvector.api-key}")
    private String apiKey;
    @Value("${dashvector.endpoint}")
    private String endpoint;

    private DashVectorCollection collection;
    private long uidBase;
    private final List<Long> uids = new ArrayList<>();
    private List<Float> queryVector;

    private DashVectorCollection collection() throws Exception {
        if (collection == null) {
            collection = new DashVectorClient(apiKey, endpoint).get(COLLECTION);
        }
        return collection;
    }

    private List<Float> queryVector() {
        if (queryVector == null) {
            queryVector = toFloatList(embeddingService.embed("测试查询向量"));
        }
        return queryVector;
    }

    private long newUid() {
        if (uidBase == 0) {
            uidBase = System.currentTimeMillis();
        }
        long uid = uidBase + uids.size() + 1;
        uids.add(uid);
        return uid;
    }

    @AfterEach
    void cleanup() throws Exception {
        for (Long uid : uids) {
            List<String> ids = new ArrayList<>();
            for (Doc doc : fetchAll(uid)) {
                ids.add(doc.getId());
            }
            if (!ids.isEmpty()) {
                collection().delete(DeleteDocRequest.builder().ids(ids).build());
            }
        }
    }

    /** 拉取该用户全部记忆(测试场景条数 <= cap+1,topK=50 必全返回) */
    private List<Doc> fetchAll(long uid) throws Exception {
        QueryDocRequest request = QueryDocRequest.builder()
                .vector(Vector.builder().value(queryVector()).build())
                .topk(50)
                .filter("user_id = " + uid)
                .outputField("content")
                .outputField("created_at")
                .build();
        Response<List<Doc>> resp = collection().query(request);
        return resp.isSuccess() && resp.getOutput() != null ? resp.getOutput() : new ArrayList<>();
    }

    private interface ThrowingCondition {
        boolean matches() throws Exception;
    }

    private static void await(String what, ThrowingCondition cond) throws Exception {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (cond.matches()) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("等待超时: " + what);
    }

    private static List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>();
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }

    @Test
    void semanticDedupKeepsOnlyOneCopy() throws Exception {
        long uid = newUid();
        // 生产最高频的重复形态:同一个问题问了两遍,回答要点近似
        // (实测 text-embedding-v3 对该形态的相似度 ≈0.956,DashVector cosine score 是距离 1-相似度)
        longTermMemoryService.remember(uid,
                "用户问过：Java 的 HashMap 是线程安全的吗\n回答要点：不是线程安全的,多线程场景建议用 ConcurrentHashMap");
        await("首条记忆可见", () -> !fetchAll(uid).isEmpty());

        longTermMemoryService.remember(uid,
                "用户问过：Java 的 HashMap 是线程安全的吗\n回答要点：HashMap 非线程安全,并发读写下建议改用 ConcurrentHashMap");

        // 去重命中 → 第二条不写入,总数保持 1
        Thread.sleep(1500);
        List<Doc> docs = fetchAll(uid);
        assert docs.size() == 1 : "相似记忆应去重,实际=" + docs.size();
    }

    @Test
    void capacityRollsOutOldest() throws Exception {
        long uid = newUid();
        String[] topics = {
                "Java 并发编程中 volatile 关键字的内存语义",
                "MySQL 的 B+ 树索引结构与最左前缀匹配规则",
                "Redis 持久化 RDB 与 AOF 两种机制的取舍",
                "TCP 三次握手与四次挥手的状态机流转",
                "Kafka 分区副本机制与 ISR 同步原理",
                "Docker namespace 与 cgroups 实现资源隔离",
                "RAG 检索增强生成中 rerank 重排序的作用",
                "JWT 无状态鉴权与刷新令牌的续期方案"
        };
        for (String topic : topics) {
            longTermMemoryService.remember(uid, "用户问过：" + topic);
            // 固定间隔:保证下一条的治理查询能看到最新写入(容量判断依赖可见的总数)
            Thread.sleep(1500);
        }
        // 8 条写入,cap=4 → 总数稳定在 4(滚动淘汰最旧)
        await("容量收敛到上限", () -> fetchAll(uid).size() <= 4);
        List<Doc> docs = fetchAll(uid);
        assert docs.size() == 4 : "总数应稳定在 cap=4,实际=" + docs.size();
        // 最旧的 volatile 主题应已被淘汰,最新的 JWT 主题应保留
        boolean hasOldest = docs.stream().anyMatch(d -> String.valueOf(
                d.getFields().get("content")).contains("volatile"));
        boolean hasNewest = docs.stream().anyMatch(d -> String.valueOf(
                d.getFields().get("content")).contains("JWT"));
        assert !hasOldest : "最旧记忆应被容量淘汰";
        assert hasNewest : "最新记忆应保留";
    }

    @Test
    void expiredMemoryIsPurgedOnNextWrite() throws Exception {
        long uid = newUid();
        // 手动插入一条 31 天前的过期记忆(绕过 remember 以控制 created_at)
        String staleId = uid + "_stale_" + System.currentTimeMillis();
        List<Float> vector = toFloatList(embeddingService.embed("很早以前的过期记忆:学习计划是每天刷一道算法题"));
        Doc stale = Doc.builder()
                .id(staleId)
                .vector(Vector.builder().value(vector).build())
                .field("user_id", uid)
                .field("content", "很早以前的过期记忆:学习计划是每天刷一道算法题")
                .field("created_at", System.currentTimeMillis() - 31L * 24 * 3600 * 1000)
                .build();
        collection().insert(InsertDocRequest.builder().doc(stale).build());
        await("过期记忆可见", () -> fetchAll(uid).size() == 1);

        longTermMemoryService.remember(uid, "用户问过：Spring 事务传播机制有哪些隔离级别");

        await("过期记忆被清理", () -> fetchAll(uid).stream()
                .noneMatch(d -> d.getId().equals(staleId)));
        List<Doc> docs = fetchAll(uid);
        assert docs.size() == 1 : "过期记忆应删除,只留新写入的 1 条,实际=" + docs.size();
    }

    @Test
    void oversizedContentIsTruncated() throws Exception {
        long uid = newUid();
        String longContent = "用户问过：请详细介绍分布式系统的 CAP 定理".repeat(20);
        longTermMemoryService.remember(uid, longContent);

        await("记忆可见", () -> !fetchAll(uid).isEmpty());
        Map<String, Object> fields = fetchAll(uid).get(0).getFields();
        String content = String.valueOf(fields.get("content"));
        assert content.length() <= 50 : "超长记忆应截断到 50,实际=" + content.length();
    }
}
