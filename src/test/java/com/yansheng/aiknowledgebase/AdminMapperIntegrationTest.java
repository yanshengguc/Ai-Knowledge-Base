package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.mapper.AdminMapper;
import com.yansheng.aiknowledgebase.vo.AdminUserVO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理端 SQL 真实执行校验(只读)。
 *
 * 单测只能证明映射逻辑,证明不了 MyBatis 能否解析 XML、SQL 能否被 MySQL 接受。
 * 本类连真实本地库跑一遍四个查询,全部为 SELECT,不写任何数据。
 * 打 integration 标签:依赖本地 MySQL,默认回归不跑。
 */
@SpringBootTest
@Tag("integration")
@ActiveProfiles("local")
class AdminMapperIntegrationTest {

    @Autowired
    private AdminMapper adminMapper;

    @Test
    void globalCountsReturnsAllFourKeys() {
        Map<String, Object> counts = adminMapper.selectGlobalCounts();

        assertNotNull(counts);
        for (String key : List.of("userCount", "knowledgeCount", "fileCount", "chunkCount")) {
            assertTrue(counts.containsKey(key), "概览缺少计数键: " + key);
            assertTrue(((Number) counts.get(key)).longValue() >= 0, key + " 不应为负数");
        }
    }

    @Test
    void fileStatusDistributionIsGroupedByStatus() {
        List<Map<String, Object>> rows = adminMapper.selectFileStatusDistribution();

        assertNotNull(rows);
        for (Map<String, Object> row : rows) {
            assertNotNull(row.get("status"), "状态分布行缺少 status");
            assertTrue(((Number) row.get("count")).longValue() > 0, "分组计数应为正数");
        }
    }

    @Test
    void globalTokenSummaryReturnsAggregateKeys() {
        Map<String, Object> summary = adminMapper.selectGlobalTokenSummary();

        assertNotNull(summary);
        for (String key : List.of("totalTokens", "totalCost", "todayTokens", "todayCost", "chatTokens", "embeddingTokens")) {
            assertTrue(summary.containsKey(key), "Token 汇总缺少键: " + key);
        }
    }

    @Test
    void userPageAndTotalUseSameFilter() {
        long total = adminMapper.countUsers(null);
        List<AdminUserVO> all = adminMapper.selectUsers(null, 5, 0);

        assertNotNull(all);
        assertTrue(all.size() <= 5, "limit=5 时最多返回 5 行");
        assertTrue(total >= all.size(), "总数不应小于当前页行数");

        String keyword = all.isEmpty() ? "no-such-user-zzz" : all.get(0).getUsername();
        List<AdminUserVO> hit = adminMapper.selectUsers(keyword, 10, 0);
        assertFalse(hit.isEmpty(), "按存在的用户名做关键词搜索应能命中");
        assertTrue(hit.stream().allMatch(u -> u.getUsername().contains(keyword)));
        assertEquals(hit.size(), adminMapper.countUsers(keyword), "count 与 list 必须同一 where 条件");
    }

    @Test
    void userRowsCarryNoPasswordField() {
        for (Field field : AdminUserVO.class.getDeclaredFields()) {
            assertNotEquals("password", field.getName());
        }
        for (AdminUserVO user : adminMapper.selectUsers(null, 5, 0)) {
            assertNotNull(user.getUsername(), "管理端用户行必须带用户名");
        }
    }
}