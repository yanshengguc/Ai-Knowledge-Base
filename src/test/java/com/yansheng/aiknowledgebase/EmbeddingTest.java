package com.yansheng.aiknowledgebase;
import com.yansheng.aiknowledgebase.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
public class EmbeddingTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private EmbeddingService embeddingService;

    // 1. 验证底层 EmbeddingModel 能直接调用成功
    @Test
    public void testEmbeddingModelDirect() {
        float[] vector = embeddingModel.embed("这是一个测试文本");
        System.out.println("【直接调用EmbeddingModel】向量维度: " + vector.length);
        System.out.print("前5个值: ");
        for (int i = 0; i < 5; i++) {
            System.out.print(vector[i] + " ");
        }
        System.out.println();
    }

    // 2. 验证 EmbeddingService 正常场景
    @Test
    public void testEmbeddingServiceNormal() {
        float[] vector = embeddingService.embed("这是测试文本");
        System.out.println("【EmbeddingService正常调用】向量维度: " + vector.length);
        if (vector.length != 1024) {
            System.out.println("警告:维度不是预期的1024!");
        } else {
            System.out.println("维度正确");
        }
    }

    // 3. 验证 EmbeddingService 空字符串场景
    @Test
    public void testEmbeddingServiceEmptyText() {
        try {
            embeddingService.embed("");
            System.out.println("【错误】空字符串没有抛出异常!");
        } catch (IllegalArgumentException e) {
            System.out.println("【正确】空字符串抛出异常: " + e.getMessage());
        }
    }

    // 4. 验证 EmbeddingService null场景
    @Test
    public void testEmbeddingServiceNullText() {
        try {
            embeddingService.embed(null);
            System.out.println("【错误】null没有抛出异常!");
        } catch (IllegalArgumentException e) {
            System.out.println("【正确】null抛出异常: " + e.getMessage());
        }
    }

    // 5. 验证 EmbeddingService 纯空格场景(这是StringUtils.hasText比简单判空更严谨的地方)
    @Test
    public void testEmbeddingServiceBlankText() {
        try {
            embeddingService.embed("   ");
            System.out.println("【错误】纯空格没有抛出异常!");
        } catch (IllegalArgumentException e) {
            System.out.println("【正确】纯空格抛出异常: " + e.getMessage());
        }
    }
}


