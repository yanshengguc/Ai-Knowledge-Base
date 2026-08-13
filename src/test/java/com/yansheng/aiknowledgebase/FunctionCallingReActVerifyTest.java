package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.service.FunctionCallingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FunctionCallingReActVerifyTest {

    @Autowired
    private FunctionCallingService functionCallingService;

    @Test
    void testMultiRoundToolCalling() {
        // 故意设计成需要连续查两个不同文件的问题
        // 逼模型必须调用两次file_trace才能完整回答
        String prompt = "请帮我分别查一下fileId=1和fileId=2这两个文件的信息,并对比一下它们的大小。";

        String result = functionCallingService.execute(prompt);

        System.out.println("=== 最终回答 ===");
        System.out.println(result);
        // 关键看控制台:">>> file_trace被调用" 这行日志打印了几次
        // 打印1次 = 模型可能只查了一个就编答案(不算通过)
        // 打印2次 = 证实框架确实在自动做多轮调用
    }
}