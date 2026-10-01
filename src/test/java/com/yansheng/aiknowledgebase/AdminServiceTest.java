package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.AdminMapper;
import com.yansheng.aiknowledgebase.service.impl.AdminServiceImpl;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.AdminOverviewVO;
import com.yansheng.aiknowledgebase.vo.AdminUserPageVO;
import com.yansheng.aiknowledgebase.vo.AdminUserVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

    private AdminServiceImpl service(String rawAdminUsernames) {
        return new AdminServiceImpl(rawAdminUsernames, adminMapper);
    }

    private void loginAs(String username) {
        UserEntity user = new UserEntity();
        user.setId(1L);
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
}