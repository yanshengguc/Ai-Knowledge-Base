package com.yansheng.aiknowledgebase;


import com.yansheng.aiknowledgebase.service.FunctionCallingService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("e2e")
@SpringBootTest
class FileSearchChainVerifyTest {

    @Autowired
    private FunctionCallingService functionCallingService;

    @Test
    void testSearchThenTraceChain() {
        // 设计成必须先检索拿到 fileId,才能查状态的"先搜后查"链式问题
        // 用"用户认证"主题:向量库里确认有该主题数据,file_search 能命中
        String prompt = "查一下讲用户认证的那份文档处理成功了没";

        String result = functionCallingService.execute(prompt);

        System.out.println("=== 最终回答 ===");
        System.out.println(result);
        // 关键看控制台日志顺序:
        //   >>> file_search被调用  (第1轮:检索拿 fileId)
        //   >>> file_trace被调用   (第2轮:用 fileId 查状态)
        // 出现"file_search → file_trace"顺序 = 序列依赖型多轮 Tool Calling 验证通过
    }
}
