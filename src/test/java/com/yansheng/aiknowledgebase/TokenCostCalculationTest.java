package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.config.TokenCostProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-资金: Token 计费计算的三类用例(正常/边界/异常)。
 * 断言的是"算出来的钱数"而非接口是否成功——
 * 计价公式: cost = prompt/1e6*inputPerM + completion/1e6*outputPerM, 6位小数 HALF_UP。
 * 单价错一位、换算基数错一个零、舍入方向错,这里都会红。
 */
class TokenCostCalculationTest {

    private TokenCostProperties props() {
        TokenCostProperties p = new TokenCostProperties();
        TokenCostProperties.ModelPrice chat = new TokenCostProperties.ModelPrice();
        chat.setInputPerM(1.5);
        chat.setOutputPerM(4.5);
        p.getModels().put("deepseek-v4-flash", chat);
        TokenCostProperties.ModelPrice embed = new TokenCostProperties.ModelPrice();
        embed.setInputPerM(0.5);
        embed.setOutputPerM(0.0);
        p.getModels().put("text-embedding-v3", embed);
        return p;
    }

    // ===== 正常:数值精确正确 =====

    @Test
    void oneMillionEachTokenCostsExactlySixYuan() {
        // 1M in * 1.5 + 1M out * 4.5 = 1.5 + 4.5 = 6.000000
        assertEquals(new BigDecimal("6.000000"),
                props().estimate("deepseek-v4-flash", 1_000_000L, 1_000_000L));
    }

    @Test
    void fractionalUsageComputedExactly() {
        // 500K*1.5/1M + 200K*4.5/1M = 0.75 + 0.90 = 1.650000
        assertEquals(new BigDecimal("1.650000"),
                props().estimate("deepseek-v4-flash", 500_000L, 200_000L));
    }

    @Test
    void embeddingOutputPricedZero() {
        assertEquals(new BigDecimal("0.500000"),
                props().estimate("text-embedding-v3", 1_000_000L, 0L));
    }

    // ===== 边界:零值/极小值/超大值/舍进位 =====

    @Test
    void zeroTokensCostZero() {
        assertEquals(new BigDecimal("0.000000"),
                props().estimate("deepseek-v4-flash", 0L, 0L));
    }

    @Test
    void singleTokenRoundsHalfUpAtSixthDecimal() {
        // 1 token in: 1/1e6*1.5 = 0.0000015 → HALF_UP 6位 → 0.000002(不是截断的0.000001)
        assertEquals(new BigDecimal("0.000002"),
                props().estimate("deepseek-v4-flash", 1L, 0L));
    }

    @Test
    void intMaxTokensNoOverflowOrPrecisionLoss() {
        // 2147483647 in: /1e6=2147.483647, *1.5=3221.2254705 → HALF_UP 6位 → 3221.225471
        assertEquals(new BigDecimal("3221.225471"),
                props().estimate("deepseek-v4-flash", Integer.MAX_VALUE, 0L));
    }

    @Test
    void resultAlwaysHasSixDecimalScale() {
        assertEquals(6, props().estimate("deepseek-v4-flash", 12345L, 6789L).scale());
    }

    // ===== 异常:未知模型/null 防御 =====

    @Test
    void unknownModelReturnsZeroNotThrow() {
        assertEquals(BigDecimal.ZERO, props().estimate("nonexistent-model", 1000L, 1000L));
    }

    @Test
    void nullModelNameReturnsZero() {
        assertEquals(BigDecimal.ZERO, props().estimate(null, 1000L, 1000L));
    }

    @Test
    void negativeTokensProduceNegativeCostObservably() {
        // 当前实现对负数不设防,负成本应被观测到(若未来加了防护,本用例提醒同步更新)
        BigDecimal cost = props().estimate("deepseek-v4-flash", -1_000_000L, 0L);
        assertTrue(cost.compareTo(BigDecimal.ZERO) < 0, "负 token 当前会得到负成本,应在上游拦截");
    }
}
