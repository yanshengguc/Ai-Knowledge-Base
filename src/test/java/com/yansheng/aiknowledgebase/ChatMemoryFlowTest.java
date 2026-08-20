package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.service.ChatService;
import com.yansheng.aiknowledgebase.service.LongTermMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@SpringBootTest
@ActiveProfiles("local")
class ChatMemoryFlowTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private LongTermMemoryService longTermMemoryService;

    @Test
    void askWritesAndRecallsMemory() throws Exception {
        StringBuilder sb = new StringBuilder();
        long uid = ThreadLocalRandom.current().nextLong(100000, 999999);

        // 1. 通过完整 ChatService 链路问一次(应写入长期记忆)
        String q1 = "我最近在准备 Java 后端实习面试,重点是 RAG 和 Agent";
        chatService.ask(uid, q1);
        sb.append("第1次 ask 完成(走完整链路)\n");

        // 2. 直接召回验证:换表述也能语义命中(证明 ask 链路真的写入了)
        List<String> hits = longTermMemoryService.recall(uid, "我面试复习什么", 3);
        sb.append("ask 后召回(" + hits.size() + "条):\n");
        for (String m : hits) sb.append("  - " + m + "\n");
        boolean hit = hits.stream().anyMatch(m -> m.contains("面试") || m.contains("RAG"));
        sb.append("命中面试/RAG 记忆: " + hit + "\n");

        Files.write(Path.of("C:/Users/yansheng/AppData/Local/Temp/dbg_memflow.txt"),
                sb.toString().getBytes(StandardCharsets.UTF_8));

        assert hit : "ChatService.ask 链路后长期记忆应可召回(写入未生效)";
    }
}
