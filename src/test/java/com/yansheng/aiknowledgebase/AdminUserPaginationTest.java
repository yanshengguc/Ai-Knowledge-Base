package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.mapper.AdminMapper;
import com.yansheng.aiknowledgebase.mapper.UserMapper;
import com.yansheng.aiknowledgebase.service.impl.AdminServiceImpl;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.AdminUserPageVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B-129 管理端用户分页 offset 溢出同源债(B-104 评审发现)。
 * page 极大时 (page-1)*size 必须以 long 运算,否则 int 溢出为负 OFFSET → MySQL 报错
 * → 通用 Exception 分支返回 code:500 系统异常(纯误报)。
 * 负向对照范本:ListPaginationTest#knowledgePageExtremePageDoesNotOverflowOffset。
 */
class AdminUserPaginationTest {

    private AdminMapper adminMapper;
    private AdminServiceImpl adminService;

    @BeforeEach
    void setUp() {
        adminMapper = mock(AdminMapper.class);
        // 白名单命中即管理员(isAdmin 快速路径,免查库)
        adminService = new AdminServiceImpl("owner", adminMapper, mock(UserMapper.class));

        UserEntity me = new UserEntity();
        me.setId(1L);
        me.setUsername("owner");
        UserContext.set(me);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    /** 极端 page:offset 必须以 long 运算;若为 int 运算会传负 offset,verify 即失败 */
    @Test
    void adminUsersExtremePageDoesNotOverflowOffset() {
        long expectedOffset = (long) (Integer.MAX_VALUE - 1) * 10;
        when(adminMapper.countUsers(null)).thenReturn(1L);
        when(adminMapper.selectUsers(null, 10, expectedOffset)).thenReturn(List.of());

        AdminUserPageVO vo = adminService.users(Integer.MAX_VALUE, 10, null);

        assertEquals(Integer.MAX_VALUE, vo.getPage());
        assertEquals(10, vo.getSize());
        assertTrue(vo.getList().isEmpty());
        verify(adminMapper).selectUsers(null, 10, expectedOffset);
    }
}
