package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.service.FunctionCallingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class FunctionCallingServiceTest {

    @Autowired
    private FunctionCallingService functionCallingService;

    @Test
    void shouldCallFileTraceTool() {

        String prompt = """
                请查询文件ID为1的来源信息。
                """;

        try {

            String result = functionCallingService.execute(prompt);

            System.out.println("========== 最终结果 ==========");
            System.out.println(result);

        } catch (Exception e) {

            System.out.println("========== 工具调用失败 ==========");
            e.printStackTrace();

        }
    }
}