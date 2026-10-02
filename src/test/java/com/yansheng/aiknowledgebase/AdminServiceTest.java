package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.dto.AdminUserCreateDTO;
import com.yansheng.aiknowledgebase.mapper.AdminMapper;
import com.yansheng.aiknowledgebase.mapper.UserMapper;
import com.yansheng.aiknowledgebase.service.impl.AdminServiceImpl;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.AdminOverviewVO;
import com.yansheng.aiknowledgebase.vo.AdminUserPageVO;
import com.yansheng.aiknowledgebase.vo.AdminUserVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 管理端只读能力单测(纯内存,不需要 Spring/DB):
 *  1. 白名单判定与安全默认(空配置 = 无人是管理员)
 *  2. requireAdmin 的拒绝口径
 *  3. 分页参数收敛(防大 size/越界 page)
 *  4. 概览聚合的映射正确性
 *  5. 用户列表结构上不含 password
 */
class AdminServiceTest {

    private final AdminMapper adminMapper = mock(AdminMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);

    private AdminServiceImpl service(String rawAdminUsernames) {
        return new AdminServiceImpl(rawAdminUsernames, adminMapper, userMapper);
    }

    private void loginAs(String username) {
        loginAs(username, 1L);
    }

    private void loginAs(String username, long id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        UserContext.set(user);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void emptyWhitelistMeansNobodyIsAdmin() {
        AdminServiceImpl svc = service("");
        assertFalse(svc.isAdmin("yan"));
        assertFalse(svc.isAdmin(null));
        assertFalse(service("   ").isAdmin(""));
    }

    @Test
    void whitelistTrimsSpacesAndMatchesExactly() {
        AdminServiceImpl svc = service(" yan , root ");
        assertTrue(svc.isAdmin("yan"));
        assertTrue(svc.isAdmin("root"));
        assertFalse(svc.isAdmin("ya"));
        assertFalse(svc.isAdmin("Yan"));
    }

    @Test
    void requireAdminRejectsAnonymousAndNonAdmin() {
        AdminServiceImpl svc = service("yan");

        UserContext.remove();
        assertEquals("权限不足", assertThrows(BusinessException.class, svc::requireAdmin).getMessage());

        loginAs("someone-else");
        assertEquals("权限不足", assertThrows(BusinessException.class, svc::requireAdmin).getMessage());

        loginAs("yan");
        assertDoesNotThrow(svc::requireAdmin);
    }

    @Test
    void overviewIsGuardedBeforeTouchingDatabase() {
        AdminServiceImpl svc = service("yan");
        loginAs("intruder");

        assertThrows(BusinessException.class, svc::overview);
        // 关键:拒绝发生在查库之前,非管理员不会触发任何聚合查询
        verifyNoInteractions(adminMapper);
    }

    @Test
    void usersIsGuardedBeforeTouchingDatabase() {
        AdminServiceImpl svc = service("yan");
        loginAs("intruder");

        assertThrows(BusinessException.class, () -> svc.users(1, 10, null));
        verifyNoInteractions(adminMapper);
    }

    @Test
    void overviewMapsCountsStatusAndTokenSummary() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");

        Map<String, Object> counts = new HashMap<>();
        counts.put("userCount", 3L);
        counts.put("knowledgeCount", 12L);
        counts.put("fileCount", 30L);
        counts.put("chunkCount", 240L);
        when(adminMapper.selectGlobalCounts()).thenReturn(counts);

        Map<String, Object> success = new HashMap<>();
        success.put("status", "SUCCESS");
        success.put("count", 28L);
        Map<String, Object> failed = new HashMap<>();
        failed.put("status", "FAILED");
        failed.put("count", 2L);
        when(adminMapper.selectFileStatusDistribution()).thenReturn(List.of(success, failed));

        Map<String, Object> tokens = new HashMap<>();
        tokens.put("totalTokens", 123456L);
        when(adminMapper.selectGlobalTokenSummary()).thenReturn(tokens);

        AdminOverviewVO vo = svc.overview();

        assertEquals(3L, vo.getUserCount());
        assertEquals(12L, vo.getKnowledgeCount());
        assertEquals(30L, vo.getFileCount());
        assertEquals(240L, vo.getChunkCount());
        assertEquals(28L, vo.getFileStatus().get("SUCCESS"));
        assertEquals(2L, vo.getFileStatus().get("FAILED"));
        assertEquals(123456L, vo.getTokenUsage().get("totalTokens"));
    }

    @Test
    void nullStatusRowFallsBackToUnknownBucket() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");
        when(adminMapper.selectGlobalCounts()).thenReturn(new HashMap<>());
        Map<String, Object> row = new HashMap<>();
        row.put("status", null);
        row.put("count", 1L);
        when(adminMapper.selectFileStatusDistribution()).thenReturn(List.of(row));
        when(adminMapper.selectGlobalTokenSummary()).thenReturn(new HashMap<>());

        assertEquals(1L, svc.overview().getFileStatus().get("UNKNOWN"));
    }

    @Test
    void usersClampsPaginationAndPassesOffset() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");
        when(adminMapper.countUsers(anyString())).thenReturn(100L);
        when(adminMapper.selectUsers(anyString(), anyInt(), anyInt())).thenReturn(List.of());

        AdminUserPageVO page = svc.users(3, 20, "  yan  ");

        assertEquals(3, page.getPage());
        assertEquals(20, page.getSize());
        assertEquals(100L, page.getTotal());
        // 关键词两侧空格需 trim;offset = (3-1)*20
        verify(adminMapper).selectUsers("yan", 20, 40);
    }

    @Test
    void usersClampsOutOfRangeInputs() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");
        when(adminMapper.selectUsers(anyString(), anyInt(), anyInt())).thenReturn(List.of());

        svc.users(0, 999, null);
        verify(adminMapper).selectUsers(null, 50, 0);

        svc.users(-5, 0, "   ");
        verify(adminMapper).selectUsers(null, 10, 0);
    }

    @Test
    void adminUserVoExposesNoPasswordField() {
        for (Field field : AdminUserVO.class.getDeclaredFields()) {
            assertNotEquals("password", field.getName(), "管理端用户列表不得回传 password");
        }
    }

    // ===== B-116 写操作:六条护栏 + 成功路径 =====

    private UserEntity user(long id, String username, String role) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setUsername(username);
        u.setRole(role);
        return u;
    }

    private AdminUserVO targetUser(long id, String username, String role) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(id);
        vo.setUsername(username);
        vo.setRole(role);
        return vo;
    }

    @Test
    void roleGrantsAdminWithoutWhitelist() {
        AdminServiceImpl svc = service("");
        when(userMapper.getUserByName("roleadmin")).thenReturn(user(9L, "roleadmin", "admin"));
        when(userMapper.getUserByName("plain")).thenReturn(user(8L, "plain", "user"));

        assertTrue(svc.isAdmin("roleadmin"));
        assertFalse(svc.isAdmin("plain"));
    }

    @Test
    void writeOperationsRequireAdminBeforeAnyWrite() {
        AdminServiceImpl svc = service("yan");
        loginAs("intruder", 7L);

        assertThrows(BusinessException.class, () -> svc.createUser(new AdminUserCreateDTO()));
        assertThrows(BusinessException.class, () -> svc.updateUserRole(2L, "admin"));
        assertThrows(BusinessException.class, () -> svc.resetUserPassword(2L, "pw"));
        assertThrows(BusinessException.class, () -> svc.deleteUser(2L));
        // 护栏 5:非管理员在任何写/聚合查询之前被拒
        verifyNoInteractions(adminMapper);
    }

    @Test
    void createUserEncodesPasswordAndDefaultsRoleToUser() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");
        when(adminMapper.selectRoleByUsername("bob")).thenReturn(null);

        AdminUserCreateDTO dto = new AdminUserCreateDTO();
        dto.setUsername("bob");
        dto.setPassword("secret123");
        dto.setNickname("Bob");

        svc.createUser(dto);

        ArgumentCaptor<String> hashed = ArgumentCaptor.forClass(String.class);
        verify(adminMapper).insertUser(eq("bob"), hashed.capture(), eq("Bob"), eq("user"));
        assertNotEquals("secret123", hashed.getValue(), "密码必须 BCrypt 哈希后入库");
        assertTrue(new BCryptPasswordEncoder().matches("secret123", hashed.getValue()));
    }

    @Test
    void createUserRejectsBlankInputsAndIllegalRole() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");

        AdminUserCreateDTO noName = new AdminUserCreateDTO();
        noName.setPassword("x");
        assertEquals("用户名不能为空", assertThrows(BusinessException.class, () -> svc.createUser(noName)).getMessage());

        AdminUserCreateDTO noPwd = new AdminUserCreateDTO();
        noPwd.setUsername("bob");
        assertEquals("密码不能为空", assertThrows(BusinessException.class, () -> svc.createUser(noPwd)).getMessage());

        AdminUserCreateDTO badRole = new AdminUserCreateDTO();
        badRole.setUsername("bob");
        badRole.setPassword("x");
        badRole.setRole("superadmin");
        assertEquals("非法的角色", assertThrows(BusinessException.class, () -> svc.createUser(badRole)).getMessage());

        verify(adminMapper, never()).insertUser(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void createUserRejectsDuplicateUsername() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");
        when(adminMapper.selectRoleByUsername("bob")).thenReturn("user");

        AdminUserCreateDTO dto = new AdminUserCreateDTO();
        dto.setUsername("bob");
        dto.setPassword("x");

        assertEquals("用户已经存在", assertThrows(BusinessException.class, () -> svc.createUser(dto)).getMessage());
        verify(adminMapper, never()).insertUser(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void createUserAllowsExplicitAdminRoleAndNullNickname() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");
        when(adminMapper.selectRoleByUsername("root2")).thenReturn(null);

        AdminUserCreateDTO dto = new AdminUserCreateDTO();
        dto.setUsername("root2");
        dto.setPassword("x");
        dto.setRole("admin");

        svc.createUser(dto);
        verify(adminMapper).insertUser(eq("root2"), anyString(), isNull(), eq("admin"));
    }

    @Test
    void updateUserRoleRejectsIllegalRoleBeforeTouchingDb() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");

        assertEquals("非法的角色", assertThrows(BusinessException.class, () -> svc.updateUserRole(2L, "root")).getMessage());
        verifyNoInteractions(adminMapper);
    }

    @Test
    void updateUserRoleRejectsSelf() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan", 1L);

        assertEquals("不能修改自己的角色", assertThrows(BusinessException.class, () -> svc.updateUserRole(1L, "user")).getMessage());
        verifyNoInteractions(adminMapper);
    }

    @Test
    void updateUserRoleRejectsMissingUser() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");
        when(adminMapper.selectUserById(2L)).thenReturn(null);

        assertEquals("用户不存在", assertThrows(BusinessException.class, () -> svc.updateUserRole(2L, "admin")).getMessage());
        verify(adminMapper, never()).updateRole(anyLong(), anyString());
    }

    @Test
    void updateUserRolePromotesTargetAndSkipsAdminSetCheck() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");
        when(adminMapper.selectUserById(2L)).thenReturn(targetUser(2L, "bob", "user"));

        svc.updateUserRole(2L, "admin");

        verify(adminMapper).updateRole(2L, "admin");
        // 提升不减少生效管理员,无需查库内管理员集合
        verify(adminMapper, never()).selectAdminUsernames();
    }

    @Test
    void updateUserRoleDemoteAllowedWhenOtherAdminsRemain() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");
        when(adminMapper.selectUserById(2L)).thenReturn(targetUser(2L, "bob", "admin"));
        when(adminMapper.selectAdminUsernames()).thenReturn(List.of("bob", "carol"));

        svc.updateUserRole(2L, "user");

        verify(adminMapper).updateRole(2L, "user");
    }

    @Test
    void demotingWhitelistOwnedRoleNeverBlocks() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");
        // 目标用户名在白名单中 -> 天然是管理员,降其表内 role 不会自锁,提前放行
        when(adminMapper.selectUserById(2L)).thenReturn(targetUser(2L, "yan", "admin"));

        svc.updateUserRole(2L, "user");

        verify(adminMapper).updateRole(2L, "user");
        verify(adminMapper, never()).selectAdminUsernames();
    }

    @Test
    void updateUserRoleBlocksRemovingLastEffectiveAdminDefensiveBranch() {
        // 护栏 2 为防御分支:一致状态下操作者自身也在生效集合内,下面用 mock 隔离出 size==1 以覆盖该分支
        AdminServiceImpl svc = service("");
        loginAs("carol", 3L);
        when(userMapper.getUserByName("carol")).thenReturn(user(3L, "carol", "admin"));
        when(adminMapper.selectUserById(2L)).thenReturn(targetUser(2L, "bob", "admin"));
        when(adminMapper.selectAdminUsernames()).thenReturn(List.of("bob"));

        assertEquals("不能移除最后一个管理员",
                assertThrows(BusinessException.class, () -> svc.updateUserRole(2L, "user")).getMessage());
        verify(adminMapper, never()).updateRole(anyLong(), anyString());
    }

    @Test
    void resetUserPasswordRejectsSelf() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan", 1L);

        assertEquals("不能重置自己的密码", assertThrows(BusinessException.class, () -> svc.resetUserPassword(1L, "newpass")).getMessage());
        verifyNoInteractions(adminMapper);
    }

    @Test
    void resetUserPasswordRejectsBlankAndMissingUser() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");

        assertEquals("密码不能为空", assertThrows(BusinessException.class, () -> svc.resetUserPassword(2L, "  ")).getMessage());

        when(adminMapper.selectUserById(2L)).thenReturn(null);
        assertEquals("用户不存在", assertThrows(BusinessException.class, () -> svc.resetUserPassword(2L, "x")).getMessage());
        verify(adminMapper, never()).updatePassword(anyLong(), anyString());
    }

    @Test
    void resetUserPasswordEncodesAndUpdates() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");
        when(adminMapper.selectUserById(2L)).thenReturn(targetUser(2L, "bob", "user"));

        svc.resetUserPassword(2L, "newpass");

        ArgumentCaptor<String> hashed = ArgumentCaptor.forClass(String.class);
        verify(adminMapper).updatePassword(eq(2L), hashed.capture());
        assertFalse("newpass".equals(hashed.getValue()), "重置密码必须 BCrypt 哈希后入库");
        assertTrue(new BCryptPasswordEncoder().matches("newpass", hashed.getValue()));
    }

    @Test
    void deleteUserRejectsSelf() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan", 1L);

        assertEquals("不能删除自己", assertThrows(BusinessException.class, () -> svc.deleteUser(1L)).getMessage());
        verifyNoInteractions(adminMapper);
    }

    @Test
    void deleteUserRejectsMissingUser() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");
        when(adminMapper.selectUserById(2L)).thenReturn(null);

        assertEquals("用户不存在", assertThrows(BusinessException.class, () -> svc.deleteUser(2L)).getMessage());
        verify(adminMapper, never()).deleteUser(anyLong());
    }

    @Test
    void deleteUserBlockedWhenOwnsKnowledgeOrFiles() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");
        when(adminMapper.selectUserById(2L)).thenReturn(targetUser(2L, "bob", "user"));
        when(adminMapper.countKnowledgeByUserId(2L)).thenReturn(1L);

        assertEquals("该用户名下仍有知识或文件,无法删除",
                assertThrows(BusinessException.class, () -> svc.deleteUser(2L)).getMessage());
        verify(adminMapper, never()).deleteUser(anyLong());
    }

    @Test
    void deleteUserBlockedByFilesEvenWhenKnowledgeEmpty() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");
        when(adminMapper.selectUserById(2L)).thenReturn(targetUser(2L, "bob", "user"));
        when(adminMapper.countKnowledgeByUserId(2L)).thenReturn(0L);
        when(adminMapper.countFilesByUserId(2L)).thenReturn(3L);

        assertThrows(BusinessException.class, () -> svc.deleteUser(2L));
        verify(adminMapper, never()).deleteUser(anyLong());
    }

    @Test
    void deleteUserSuccessRemovesRow() {
        AdminServiceImpl svc = service("yan");
        loginAs("yan");
        when(adminMapper.selectUserById(2L)).thenReturn(targetUser(2L, "bob", "user"));
        when(adminMapper.countKnowledgeByUserId(2L)).thenReturn(0L);
        when(adminMapper.countFilesByUserId(2L)).thenReturn(0L);

        svc.deleteUser(2L);

        verify(adminMapper).deleteUser(2L);
    }

    @Test
    void deleteUserBlocksRemovingLastEffectiveAdminDefensiveBranch() {
        AdminServiceImpl svc = service("");
        loginAs("carol", 3L);
        when(userMapper.getUserByName("carol")).thenReturn(user(3L, "carol", "admin"));
        when(adminMapper.selectUserById(2L)).thenReturn(targetUser(2L, "bob", "admin"));
        when(adminMapper.countKnowledgeByUserId(2L)).thenReturn(0L);
        when(adminMapper.countFilesByUserId(2L)).thenReturn(0L);
        when(adminMapper.selectAdminUsernames()).thenReturn(List.of("bob"));

        assertEquals("不能移除最后一个管理员",
                assertThrows(BusinessException.class, () -> svc.deleteUser(2L)).getMessage());
        verify(adminMapper, never()).deleteUser(anyLong());
    }
}