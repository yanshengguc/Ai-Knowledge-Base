package com.yansheng.aiknowledgebase;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 存量密码迁移(一次性工具):把历史明文密码升级为 BCrypt 哈希。
 * 幂等:已哈希(以 $2 开头)的记录跳过,可安全重复执行。
 * 背景:2026-08-17 密码改 BCrypt 前,已注册用户的 password 字段是明文,
 *       BCrypt matches 无法验证明文 → 登录会报"密码错误",此迁移修复。
 */
@SpringBootTest
@ActiveProfiles("local")
class PasswordMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void migrateLegacyPlaintextPasswords() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, password FROM user WHERE password NOT LIKE '$2%'");

        int migrated = 0;
        for (Map<String, Object> row : rows) {
            String raw = String.valueOf(row.get("password"));
            String hash = encoder.encode(raw);
            jdbcTemplate.update("UPDATE user SET password = ? WHERE id = ?",
                    hash, row.get("id"));
            migrated++;
        }
        System.out.println("已迁移明文密码用户数: " + migrated);

        // 验证:迁移后不应再存在明文密码
        Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user WHERE password NOT LIKE '$2%'", Long.class);
        assertEquals(0L, remaining, "迁移后不应残留明文密码");
        System.out.println("验证通过:全部密码已是 BCrypt 哈希,残留明文数=" + remaining);
    }
}
