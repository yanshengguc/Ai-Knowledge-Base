package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.dto.AdminUserCreateDTO;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.AdminMapper;
import com.yansheng.aiknowledgebase.mapper.UserMapper;
import com.yansheng.aiknowledgebase.service.AdminService;
import com.yansheng.aiknowledgebase.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理端写链路真实执行校验(B-116)。
 *
 * 连本地 MySQL 走「建临时用户 -> 改角色 -> 重置密码 -> 删除」全链路,断言落库结果;
 * 用例自建自清(AfterEach 兜底删除),测完不在库里留垃圾数据。
 * 鉴权:把操作者用户名放进 admin.usernames(白名单),不依赖库内既有管理员数据。
 * 打 integration 标签:依赖本地 MySQL,默认回归不跑。
 */
@SpringBootTest(properties = "admin.usernames=aikb_it_operator")
@Tag("integration")
@ActiveProfiles("local")
class AdminWriteIntegrationTest {

    private static final String OPERATOR = "aikb_it_operator";

    @Autowired
    private AdminService adminService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AdminMapper adminMapper;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String suffix = Long.toString(System.currentTimeMillis());

    /** 本用例建出的目标用户名;AfterEach 据此兜底清理 */
    private String targetUsername;

    private void loginAsOperator() {
        UserEntity operator = new UserEntity();
        operator.setId(-1L); // 操作者不入库:白名单判据不依赖行存在,避免污染 user 表
        operator.setUsername(OPERATOR);
        UserContext.set(operator);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
        if (targetUsername != null) {
            UserEntity leftover = userMapper.getUserByName(targetUsername);
            if (leftover != null) {
                adminMapper.deleteUser(leftover.getId());
            }
        }
    }

    @Test
    void fullWriteChainCreatePromoteResetDelete() {
        loginAsOperator();
        targetUsername = "aikb_it_user_" + suffix;

        // 1. 建号(role 省略 -> 默认 user)
        AdminUserCreateDTO create = new AdminUserCreateDTO();
        create.setUsername(targetUsername);
        create.setPassword("init-pass-123");
        create.setNickname("IT User");
        adminService.createUser(create);

        UserEntity created = userMapper.getUserByName(targetUsername);
        assertNotNull(created, "建号后应能查到该用户");
        assertEquals("user", created.getRole(), "role 默认应为 user");
        assertTrue(encoder.matches("init-pass-123", created.getPassword()), "密码应 BCrypt 落库");
        long id = created.getId();

        // 2. 改角色:user -> admin
        adminService.updateUserRole(id, "admin");
        assertEquals("admin", adminMapper.selectRoleByUsername(targetUsername), "改角色应落库");

        // 3. 重置密码
        adminService.resetUserPassword(id, "reset-pass-456");
        UserEntity afterReset = userMapper.findById(id);
        assertNotNull(afterReset);
        assertTrue(encoder.matches("reset-pass-456", afterReset.getPassword()), "重置后新密码应可匹配");
        assertFalse(encoder.matches("init-pass-123", afterReset.getPassword()), "旧密码应失效");

        // 4. 删除
        adminService.deleteUser(id);
        assertNull(userMapper.findById(id), "删除后不应再查到该用户");
        targetUsername = null; // 已清理
    }

    @Test
    void writeChainRejectsIllegalRoleAndMissingUser() {
        loginAsOperator();
        targetUsername = "aikb_it_user_" + suffix;

        AdminUserCreateDTO create = new AdminUserCreateDTO();
        create.setUsername(targetUsername);
        create.setPassword("init-pass-123");
        adminService.createUser(create);
        long id = userMapper.getUserByName(targetUsername).getId();

        assertEquals("非法的角色",
                assertThrows(BusinessException.class, () -> adminService.updateUserRole(id, "root")).getMessage());

        assertEquals("用户不存在",
                assertThrows(BusinessException.class, () -> adminService.deleteUser(id + 999999L)).getMessage());
    }

    @Test
    void deletePreconditionCountsAndLookupAreUsable() {
        loginAsOperator();
        // 护栏 3 的读路径真机可用性:不存在的用户名下计数为 0、查不到用户
        assertEquals(0L, adminMapper.countKnowledgeByUserId(-1L));
        assertEquals(0L, adminMapper.countFilesByUserId(-1L));
        assertNull(adminMapper.selectUserById(-1L));
    }
}