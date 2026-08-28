package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.service.ChatQuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-资金: 聊天每日配额边界(token-limit=1000 测试专用上下文)。
 * 配额是成本治理最后一层:限流挡突发,配额挡"脚本全天匀速烧 key"。
 * 边界语义:当日用量 >= 上限即拒绝新对话(额度耗尽后不再放行);
 * 豁免用户(作者本人)不限额;多条记录跨行聚合后一起算(SUM 口径,不是单条)。
 * 数据走真实 MySQL(token_usage 插入 + 测后按 user_id 清理),不走 mock。
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "chat.daily-quota.enabled=true",
        "chat.daily-quota.token-limit=1000",
        "chat.daily-quota.exempt-users=quota-exempt"
})
class ChatDailyQuotaTest {

    @Autowired
    private ChatQuotaService chatQuotaService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long uid;

    private long uid() {
        if (uid == 0) {
            uid = System.currentTimeMillis();
        }
        return uid;
    }

    /** 按真实记账口径造数据:type='chat' 的 token_usage 行 */
    private void used(long totalTokens) {
        jdbcTemplate.update(
                "insert into token_usage (user_id, model, type, prompt_tokens, completion_tokens, total_tokens, cost_cny) "
                        + "values (?, 'deepseek-v4-flash', 'chat', 0, 0, ?, 0)",
                uid(), totalTokens);
    }

    @AfterEach
    void cleanup() {
        if (uid != 0) {
            jdbcTemplate.update("delete from token_usage where user_id = ?", uid());
        }
    }

    // ===== 边界:999 放行 / 1000 拒绝(>= 即超) =====

    @Test
    void underLimitAllowed() {
        used(999);
        assertDoesNotThrow(() -> chatQuotaService.check(uid(), "normal-user"), "999 < 1000 不应被拒");
    }

    @Test
    void exactlyAtLimitRejected() {
        used(1000);
        BusinessException e = assertThrows(BusinessException.class,
                () -> chatQuotaService.check(uid(), "normal-user"));
        assertTrue(e.getMessage().contains("额度"), "提示应包含'额度',实际=" + e.getMessage());
    }

    @Test
    void overLimitRejected() {
        used(1001);
        assertThrows(BusinessException.class, () -> chatQuotaService.check(uid(), "normal-user"));
    }

    // ===== 聚合口径:多条记录 SUM 后一起算 =====

    @Test
    void multipleRecordsSummedBeforeCompare() {
        used(600);
        assertDoesNotThrow(() -> chatQuotaService.check(uid(), "normal-user"), "累计 600 未达上限");
        used(400);
        assertThrows(BusinessException.class,
                () -> chatQuotaService.check(uid(), "normal-user"), "累计 1000 达上限应拒");
    }

    // ===== 豁免用户:额度再大也放行 =====

    @Test
    void exemptUserUnlimited() {
        used(99999);
        assertDoesNotThrow(() -> chatQuotaService.check(uid(), "quota-exempt"), "豁免用户不限额");
        assertThrows(BusinessException.class,
                () -> chatQuotaService.check(uid(), "normal-user"), "同额度非豁免用户应被拒(对照)");
    }
}
