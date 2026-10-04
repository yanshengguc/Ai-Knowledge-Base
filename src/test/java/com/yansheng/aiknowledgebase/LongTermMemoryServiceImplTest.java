package com.yansheng.aiknowledgebase;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aliyun.dashvector.DashVectorCollection;
import com.aliyun.dashvector.models.Doc;
import com.aliyun.dashvector.models.DocOpResult;
import com.aliyun.dashvector.models.requests.DeleteDocRequest;
import com.aliyun.dashvector.models.requests.InsertDocRequest;
import com.aliyun.dashvector.models.requests.QueryDocRequest;
import com.aliyun.dashvector.models.responses.Response;
import com.yansheng.aiknowledgebase.service.EmbeddingService;
import com.yansheng.aiknowledgebase.service.impl.LongTermMemoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B-125 长期记忆降级回归:顶层 isSuccess() 为 true 时逐条仍可能 code != 0,
 * 修复前这些失败被静默吞掉(写入侧、治理删除侧均无观测)。
 * 降级契约:任何异常不影响主流程,故修复后必须只 warn 不抛。
 */
class LongTermMemoryServiceImplTest {

    private DashVectorCollection collection;
    private EmbeddingService embeddingService;
    private LongTermMemoryServiceImpl service;

    @BeforeEach
    void setUp() {
        collection = mock(DashVectorCollection.class);
        embeddingService = mock(EmbeddingService.class);
        when(embeddingService.embed(any(String.class))).thenReturn(new float[]{0.1f, 0.2f});

        service = new LongTermMemoryServiceImpl(embeddingService);
        ReflectionTestUtils.setField(service, "collection", collection);
        ReflectionTestUtils.setField(service, "contentMaxLength", 200);
        ReflectionTestUtils.setField(service, "dedupThreshold", 0.92f);
        ReflectionTestUtils.setField(service, "maxPerUser", 500);
        ReflectionTestUtils.setField(service, "retentionDays", 180);
    }

    /** 顶层成功 + 逐条含一个失败(code=-2027) */
    private Response<List<DocOpResult>> perDocFailureResp(String okId, String failId) {
        List<DocOpResult> ops = new ArrayList<>();
        ops.add(DocOpResult.builder().id(okId).code(0).message("Success").build());
        ops.add(DocOpResult.builder().id(failId).code(-2027).message("Duplicate key").build());
        return Response.create(0, "Success", "req", ops);
    }

    private ListAppender<ILoggingEvent> attach() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(LongTermMemoryServiceImpl.class)).addAppender(appender);
        return appender;
    }

    private void detach(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(LongTermMemoryServiceImpl.class)).detachAppender(appender);
    }

    private boolean warned(ListAppender<ILoggingEvent> appender, String fragment) {
        return appender.list.stream().anyMatch(e -> e.getLevel() == Level.WARN
                && e.getFormattedMessage() != null
                && e.getFormattedMessage().contains(fragment));
    }

    @Test
    void rememberDegradesAndWarnsWhenPerDocInsertFails() {
        // governanceEnabled=false → 跳过治理直达 insert
        ReflectionTestUtils.setField(service, "governanceEnabled", false);
        when(collection.insert(any(InsertDocRequest.class)))
                .thenReturn(perDocFailureResp("u1_ok", "u1_dup"));

        ListAppender<ILoggingEvent> appender = attach();
        try {
            // 降级:逐条写入失败不得抛异常,不中断对话链路
            assertDoesNotThrow(() -> service.remember(1L, "一条普通记忆"));
        } finally {
            detach(appender);
        }

        assertTrue(warned(appender, "-2027"), "应 WARN 出逐条失败的 code");
        assertTrue(warned(appender, "失败条数=1"), "应 WARN 出失败条数");
        assertTrue(warned(appender, "u1_dup"), "应 WARN 出首个失败 id");
    }

    @Test
    void rememberDegradesAndWarnsWhenGovernanceDeleteFails() {
        ReflectionTestUtils.setField(service, "governanceEnabled", true);
        when(collection.insert(any(InsertDocRequest.class)))
                .thenReturn(Response.create(0, "Success", "req-i", new ArrayList<>()));

        // 一条早于保留期的过期记忆(created_at 200 天前)→ 触发治理删除
        long staleTs = System.currentTimeMillis() - 200L * 24 * 3600 * 1000;
        Doc stale = Doc.builder()
                .id("u2_stale")
                .score(0.5f)
                .field("created_at", staleTs)
                .build();
        List<Doc> hits = new ArrayList<>();
        hits.add(stale);
        when(collection.query(any(QueryDocRequest.class)))
                .thenReturn(Response.create(0, "Success", "req-q", hits));
        when(collection.delete(any(DeleteDocRequest.class)))
                .thenReturn(perDocFailureResp("u2_ok", "u2_stale"));

        ListAppender<ILoggingEvent> appender = attach();
        try {
            assertDoesNotThrow(() -> service.remember(2L, "触发治理的记忆"));
        } finally {
            detach(appender);
        }

        assertTrue(warned(appender, "-2027"), "删除响应不再被丢弃:应 WARN 出逐条失败 code");
        assertTrue(warned(appender, "失败条数=1"), "应 WARN 出删除失败条数");
        assertTrue(warned(appender, "u2_stale"), "应 WARN 出首个失败 id");
    }
}
