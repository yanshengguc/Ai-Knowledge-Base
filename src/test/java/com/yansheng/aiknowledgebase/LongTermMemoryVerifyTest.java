package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.service.LongTermMemoryService;
import org.junit.jupiter.api.Tag;
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
@Tag("e2e")
@ActiveProfiles("local")
class LongTermMemoryVerifyTest {

    @Autowired
    private LongTermMemoryService longTermMemoryService;

    @Test
    void verifyLongTermMemory() throws Exception {
        StringBuilder sb = new StringBuilder();
        long uidA = ThreadLocalRandom.current().nextLong(100000, 999999);
        long uidB = uidA + 1;

        // 1. 写入两条记忆(用户A)
        longTermMemoryService.remember(uidA, "用户偏好:回答尽量简洁,用要点列表");
        longTermMemoryService.remember(uidA, "用户正在准备 Java 后端面试,关注 RAG 和 Agent");
        sb.append("=== 已写入 2 条记忆(userId=" + uidA + ") ===\n");

        // 2. 用户A 语义召回(换表述也能命中)
        List<String> hitA = longTermMemoryService.recall(uidA, "我面试要注意什么", 3);
        sb.append("用户A 召回(" + hitA.size() + "条):\n");
        for (String m : hitA) sb.append("  - " + m + "\n");

        // 3. 用户B 检索同样问题(隔离验证:应 0 条)
        List<String> hitB = longTermMemoryService.recall(uidB, "我面试要注意什么", 3);
        sb.append("用户B 召回(" + hitB.size() + "条,应为0):\n");
        for (String m : hitB) sb.append("  - " + m + "\n");

        sb.append("=== 结论 ===\n");
        sb.append("用户A 命中 >=1 且包含面试记忆: " + (hitA.stream().anyMatch(m -> m.contains("面试"))) + "\n");
        sb.append("用户B 隔离(0 条): " + hitB.isEmpty() + "\n");

        Files.write(Path.of("C:/Users/yansheng/AppData/Local/Temp/dbg_memory.txt"),
                sb.toString().getBytes(StandardCharsets.UTF_8));

        // 断言
        assert hitB.isEmpty() : "用户B 不应命中用户A 的记忆(隔离失败)";
        assert hitA.stream().anyMatch(m -> m.contains("面试")) : "用户A 应语义命中面试记忆";
    }
}
