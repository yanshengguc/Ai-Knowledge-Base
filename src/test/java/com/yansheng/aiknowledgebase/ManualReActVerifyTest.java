package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.service.ManualReActVerifyService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("e2e")
@SpringBootTest
class ManualReActVerifyTest {

    @Autowired
    private ManualReActVerifyService manualReActVerifyService;

    @Test
    void testManualLoop() {
        String prompt = "请帮我分别查一下fileId=1和fileId=2这两个文件的信息,并对比一下它们的大小。";

        String result = manualReActVerifyService.executeManually(prompt);

        System.out.println("=== 手动循环最终回答 ===");
        System.out.println(result);
        // 关注控制台:">>> 手动循环第N轮" 打印了几次,和之前自动模式的2次对比
    }
}