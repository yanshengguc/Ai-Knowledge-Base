package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.service.RerankService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

@SpringBootTest
@ActiveProfiles("local")
class RerankSmokeTest {

    @Autowired
    private RerankService rerankService;

    @Test
    void rerankDegradesGracefully() throws Exception {
        List<SearchResult> candidates = Arrays.asList(
                new SearchResult(1L, 1L, "NBA basketball game results and player statistics", 0.5),
                new SearchResult(2L, 2L, "JVM garbage collection: CMS and G1 collector comparison, heap tuning", 0.5),
                new SearchResult(3L, 3L, "Spring Boot async upload with CompletableFuture", 0.5)
        );
        List<SearchResult> result = rerankService.rerank("JVM垃圾回收调优", candidates, 3);
        StringBuilder sb = new StringBuilder();
        sb.append("=== 重排结果 ===\n");
        for (SearchResult r : result) {
            sb.append(String.format("  fileId=%s | %s%n", r.getFileId(),
                    r.getContent().substring(0, Math.min(50, r.getContent().length()))));
        }
        sb.append("验证:重排失败必须降级返回原顺序(容错),当前顺序 = " + result.get(0).getFileId() + "\n");
        java.nio.file.Files.write(java.nio.file.Path.of("C:/Users/yansheng/AppData/Local/Temp/dbg_rerank.txt"),
                sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // 断言:1) 不抛异常 2) 返回数与候选一致 3) 顺序稳定(降级 = 原顺序)
        assert result.size() == 3 : "必须返回全部候选";
        assert result.get(0).getFileId() == 1L : "降级应保持原顺序(fileId=1 在前)";
    }
}
