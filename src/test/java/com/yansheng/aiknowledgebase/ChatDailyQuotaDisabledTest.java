package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.service.ChatQuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * P1-配置: 配额开关关闭时检查完全跳过(chat.daily-quota.enabled=false)。
 * 防的是"配置形同虚设":开关写了但代码里没接,或者接了但判断反了——
 * 用超限用量证明关掉后照样放行。
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "chat.daily-quota.enabled=false",
        "chat.daily-quota.token-limit=100"
})
class ChatDailyQuotaDisabledTest {

    @Autowired
    private ChatQuotaService chatQuotaService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long uid;

    @AfterEach
    void cleanup() {
        if (uid != 0) {
            jdbcTemplate.update("delete from token_usage where user_id = ?", uid);
        }
    }

    @Test
    void disabledQuotaSkipsCheckEvenOverLimit() {
        uid = System.currentTimeMillis();
        jdbcTemplate.update(
                "insert into token_usage (user_id, model, type, prompt_tokens, completion_tokens, total_tokens, cost_cny) "
                        + "values (?, 'deepseek-v4-flash', 'chat', 0, 0, 99999, 0)",
                uid);
        assertDoesNotThrow(() -> chatQuotaService.check(uid, "normal-user"), "开关关闭时超限也应放行");
    }
}
