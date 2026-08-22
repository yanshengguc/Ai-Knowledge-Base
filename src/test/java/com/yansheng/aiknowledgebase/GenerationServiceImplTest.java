package com.yansheng.aiknowledgebase;


import com.yansheng.aiknowledgebase.service.GenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class GenerationServiceImplTest {

    @Autowired
    private GenerationService generationService;

    @Test
    void testGenerate_normalPrompt() {
        String answer = generationService.generate("请用一句话介绍Spring Boot");
        System.out.println("回答：" + answer);
        assertNotNull(answer);
        assertFalse(answer.isBlank());
    }

    @Test
    void testGenerate_emptyPrompt_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> generationService.generate(""));
    }

    @Test
    void testGenerate_blankPrompt_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> generationService.generate("   "));
    }
}