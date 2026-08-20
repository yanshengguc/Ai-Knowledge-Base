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
    void rerankSortsByRelevance() throws Exception {
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
        sb.append("=== 真实重排(硅基流动 bge-reranker-v2-m3) ===\n");
        for (SearchResult r : result) {
            sb.append(String.format("  fileId=%s | %s%n", r.getFileId(),
                    r.getContent().substring(0, Math.min(50, r.getContent().length()))));
        }
        sb.append("第一个(fileId=" + result.get(0).getFileId() + ")应为 JVM(2)\n");
        java.nio.file.Files.write(java.nio.file.Path.of("C:/Users/yansheng/AppData/Local/Temp/dbg_rerank.txt"),
                sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // 断言:JVM 文档必须排第一(真实重排生效)
        assert result.size() == 3 : "必须返回全部候选";
        assert result.get(0).getFileId() == 2L : "重排后 JVM 文档应排第一,实际=" + result.get(0).getFileId();
    }
}
