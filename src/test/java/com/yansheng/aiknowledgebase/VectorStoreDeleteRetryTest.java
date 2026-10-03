package com.yansheng.aiknowledgebase;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aliyun.dashvector.DashVectorCollection;
import com.aliyun.dashvector.models.CollectionMeta;
import com.aliyun.dashvector.models.Doc;
import com.aliyun.dashvector.models.DocOpResult;
import com.aliyun.dashvector.models.requests.DeleteDocRequest;
import com.aliyun.dashvector.models.requests.QueryDocRequest;
import com.aliyun.dashvector.models.responses.Response;
import com.yansheng.aiknowledgebase.service.impl.VectorStoreServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B-126 deleteByFileId 逐条删除校验回归:
 * 顶层 isSuccess() 为 true 时逐条仍可能 code != 0(与写入侧 B-117 同源),
 * 修复前会被"乐观计数 + 末页早退"掩盖成假成功,失败条永久残留为孤儿向量。
 * 用 mock DashVectorCollection 做纯单测(不触网、无 integration tag):
 * 1) 末页部分失败必须重试而非早退;2) 持续失败必须触顶告警、不得假报"已清理",失败条不计入累计。
 */
class VectorStoreDeleteRetryTest {

    private static final long FILE_ID = 42L;
    private static final int DIM = 4;

    private DashVectorCollection collection;
    private VectorStoreServiceImpl store;

    @BeforeEach
    void setUp() {
        collection = mock(DashVectorCollection.class);
        CollectionMeta meta = mock(CollectionMeta.class);
        when(meta.getDimension()).thenReturn(DIM);
        when(collection.getCollectionMeta()).thenReturn(meta);

        store = new VectorStoreServiceImpl();
        ReflectionTestUtils.setField(store, "collection", collection);
    }

    private Response<List<Doc>> queryOk(String... ids) {
        List<Doc> docs = new ArrayList<>(ids.length);
        for (String id : ids) {
            docs.add(Doc.builder().id(id).build());
        }
        return Response.create(0, "Success", "req-query", docs);
    }

    /** 参数形如 "11:0" / "12:-2027",分别表示 id=11 成功、id=12 失败 */
    private Response<List<DocOpResult>> deleteResp(String... perId) {
        List<DocOpResult> ops = new ArrayList<>(perId.length);
        for (String item : perId) {
            int sep = item.indexOf(':');
            String id = item.substring(0, sep);
            int code = Integer.parseInt(item.substring(sep + 1));
            ops.add(DocOpResult.builder()
                    .id(id)
                    .code(code)
                    .message(code == 0 ? "Success" : "Delete failed")
                    .build());
        }
        return Response.create(0, "Success", "req-delete", ops);
    }

    private boolean logged(ListAppender<ILoggingEvent> appender, String fragment) {
        return appender.list.stream().anyMatch(e -> e.getFormattedMessage() != null
                && e.getFormattedMessage().contains(fragment));
    }

    @Test
    void partialFailureOnLastPageShouldRetryInsteadOfEarlyExit() {
        // 第 1 轮末页只返回 2 条(< pageSize=100),其中 id=12 删除失败(code=-2027)
        when(collection.query(any(QueryDocRequest.class)))
                .thenReturn(queryOk("11", "12"))
                .thenReturn(queryOk("12"));
        when(collection.delete(any(DeleteDocRequest.class)))
                .thenReturn(deleteResp("11:0", "12:-2027"))
                .thenReturn(deleteResp("12:0"));

        store.deleteByFileId(FILE_ID);

        // 修复前:ids.size()=2 < pageSize 直接早退 → query/delete 各只 1 次,id=12 永久残留
        verify(collection, times(2)).query(any(QueryDocRequest.class));
        verify(collection, times(2)).delete(any(DeleteDocRequest.class));
    }

    @Test
    void persistentFailureShouldNotReportSuccessAndShouldStopAtSafetyLimit() {
        // 删除始终失败:失败条每轮都被重新查出,不得早退,直到 maxRounds=50 触顶
        when(collection.query(any(QueryDocRequest.class))).thenReturn(queryOk("12"));
        when(collection.delete(any(DeleteDocRequest.class))).thenReturn(deleteResp("12:-2027"));

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(VectorStoreServiceImpl.class);
        logger.addAppender(appender);
        try {
            store.deleteByFileId(FILE_ID);
        } finally {
            logger.detachAppender(appender);
        }

        verify(collection, times(50)).delete(any(DeleteDocRequest.class));
        assertFalse(logged(appender, "已清理向量"), "持续失败不得假报'已清理向量'");
        assertTrue(logged(appender, "向量清理达到安全上限"), "应触顶告警而非静默返回");
        assertTrue(logged(appender, "累计删除=0"), "失败条不得计入累计删除");
    }
}
