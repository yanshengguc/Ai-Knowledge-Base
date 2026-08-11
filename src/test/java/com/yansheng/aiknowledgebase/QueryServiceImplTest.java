package com.yansheng.aiknowledgebase;
import com.yansheng.aiknowledgebase.entity.QueryResponse;
import com.yansheng.aiknowledgebase.service.QueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class QueryServiceImplTest {

    @Autowired
    private QueryService queryService;

    @Test
    void testQuery_javaRelatedQuestion() {
        QueryResponse response = queryService.query("Java中HashMap的实现原理是什么？"); // 换成和你实际存的内容匹配的问题

        System.out.println("回答：" + response.getAnswer());
        System.out.println("引用来源数量：" + response.getSources().size());
        response.getSources().forEach(s ->
                System.out.println("  来源chunkId=" + s.getChunkId() + ", score=" + s.getScore())
        );

        assertNotNull(response);
        assertFalse(response.getAnswer().isBlank());
    }
}