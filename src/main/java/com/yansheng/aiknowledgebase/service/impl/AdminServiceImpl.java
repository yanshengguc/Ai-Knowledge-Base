package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.dto.AdminUserCreateDTO;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.AdminMapper;
import com.yansheng.aiknowledgebase.mapper.UserMapper;
import com.yansheng.aiknowledgebase.service.AdminService;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.AdminOverviewVO;
import com.yansheng.aiknowledgebase.vo.AdminUserPageVO;
import com.yansheng.aiknowledgebase.vo.AdminUserVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminServiceImpl implements AdminService {

    /** 分页上限:管理端一次最多 50 行,防大 size 拖垮 2C2G 小机 */
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** user.role 二值模型(B-116) */
    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_USER = "user";
    private static final Set<String> ALLOWED_ROLES = Set.of(ROLE_ADMIN, ROLE_USER);

    private final Set<String> adminUsernames;
    private final AdminMapper adminMapper;
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminServiceImpl(@Value("${admin.usernames:}") String rawAdminUsernames,
                            AdminMapper adminMapper,
                            UserMapper userMapper) {
        this.adminUsernames = Arrays.stream(rawAdminUsernames.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.adminMapper = adminMapper;
        this.userMapper = userMapper;
    }

    @Override
    public boolean isAdmin(String username) {
        if (username == null || username.isBlank()) {
            // 空配置 = 无人是管理员(安全默认:忘配环境变量时管理端整体关闭,而不是全开)
            return false;
        }
        // 快速路径:白名单命中直接判定,不查库(存量白名单管理员不受表内 role 影响,平滑过渡)
        if (adminUsernames.contains(username)) {
            return true;
        }
        // 叠加判据:表内 role='admin'(走 UserMapper,不触碰管理端聚合查询)
        UserEntity user = userMapper.getUserByName(username);
        return user != null && ROLE_ADMIN.equals(user.getRole());
    }

    @Override
    public void requireAdmin() {
        if (!isAdmin(UserContext.getUsername())) {
            // 统一话术,不区分"未登录/已登录但非管理员",避免探测管理端白名单
            throw new BusinessException("权限不足");
        }
    }

    @Override
    public AdminOverviewVO overview() {
        requireAdmin();

        Map<String, Object> counts = adminMapper.selectGlobalCounts();
        AdminOverviewVO vo = new AdminOverviewVO();
        vo.setUserCount(asLong(counts, "userCount"));
        vo.setKnowledgeCount(asLong(counts, "knowledgeCount"));
        vo.setFileCount(asLong(counts, "fileCount"));
        vo.setChunkCount(asLong(counts, "chunkCount"));

        Map<String, Long> status = new LinkedHashMap<>();
        for (Map<String, Object> row : adminMapper.selectFileStatusDistribution()) {
            Object key = row.get("status");
            status.put(key == null ? "UNKNOWN" : key.toString(), asLong(row, "count"));
        }
        vo.setFileStatus(status);
        vo.setTokenUsage(adminMapper.selectGlobalTokenSummary());
        return vo;
    }

    @Override
    public AdminUserPageVO users(int page, int size, String keyword) {
        requireAdmin();

        int safePage = Math.max(page, 1);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        String safeKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();

        AdminUserPageVO vo = new AdminUserPageVO();
        vo.setPage(safePage);
        vo.setSize(safeSize);
        vo.setTotal(adminMapper.countUsers(safeKeyword));
        // B-129:offset 以 long 运算,page 极大时 (safePage-1)*safeSize 不会溢出为负 OFFSET(与 knowledge/page 口径一致)
        List<AdminUserVO> list = adminMapper.selectUsers(safeKeyword, safeSize, (long) (safePage - 1) * safeSize);
        vo.setList(list);
        return vo;
    }

    // ===== B-116 写操作(逐条落实六条护栏:每个方法第一行 requireAdmin,拒绝一律发生在任何写调用之前) =====

    @Override
    public void createUser(AdminUserCreateDTO dto) {
        requireAdmin();

        String username = dto == null ? null : dto.getUsername();
        if (username == null || username.isBlank()) {
            throw new BusinessException("用户名不能为空");
        }
        username = username.trim();

        String password = dto.getPassword();
        if (password == null || password.isBlank()) {
            throw new BusinessException("密码不能为空");
        }

        String role = dto.getRole() == null || dto.getRole().isBlank() ? ROLE_USER : dto.getRole().trim();
        if (!ALLOWED_ROLES.contains(role)) {
            throw new BusinessException("非法的角色");
        }

        // 唯一性:role NOT NULL,非空即该用户名已存在(免额外 count SQL)
        if (adminMapper.selectRoleByUsername(username) != null) {
            throw new BusinessException("用户已经存在");
        }

        String nickname = dto.getNickname() == null || dto.getNickname().isBlank() ? null : dto.getNickname().trim();
        adminMapper.insertUser(username, passwordEncoder.encode(password), nickname, role);
    }

    @Override
    public void updateUserRole(long id, String role) {
        requireAdmin();

        if (role == null || !ALLOWED_ROLES.contains(role)) {
            throw new BusinessException("非法的角色");
        }

        Long selfId = UserContext.getUserId();
        if (selfId != null && selfId == id) {
            throw new BusinessException("不能修改自己的角色");
        }

        AdminUserVO target = adminMapper.selectUserById(id);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }
        if (!ROLE_ADMIN.equals(role)) {
            // 仅降角色可能移除生效管理员
            ensureNotRemovingLastEffectiveAdmin(target.getUsername(), target.getRole());
        }
        adminMapper.updateRole(id, role);
    }

    @Override
    public void resetUserPassword(long id, String password) {
        requireAdmin();

        if (password == null || password.isBlank()) {
            throw new BusinessException("密码不能为空");
        }

        Long selfId = UserContext.getUserId();
        if (selfId != null && selfId == id) {
            throw new BusinessException("不能重置自己的密码");
        }
        if (adminMapper.selectUserById(id) == null) {
            throw new BusinessException("用户不存在");
        }
        adminMapper.updatePassword(id, passwordEncoder.encode(password));
    }

    @Override
    public void deleteUser(long id) {
        requireAdmin();

        Long selfId = UserContext.getUserId();
        if (selfId != null && selfId == id) {
            throw new BusinessException("不能删除自己");
        }

        AdminUserVO target = adminMapper.selectUserById(id);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }

        // 护栏 3:删除前置——名下仍有知识/文件则拒绝(knowledge.user_id 无外键,直删会留孤儿数据)
        if (adminMapper.countKnowledgeByUserId(id) > 0 || adminMapper.countFilesByUserId(id) > 0) {
            throw new BusinessException("该用户名下仍有知识或文件,无法删除");
        }

        ensureNotRemovingLastEffectiveAdmin(target.getUsername(), target.getRole());
        adminMapper.deleteUser(id);
    }

    /**
     * 护栏 2:不得移除最后一个生效管理员(防自锁)。
     * 生效集合 = 白名单 ∪ 表内 role='admin' 的用户名。
     * 白名单用户在配置里恒为管理员,改/删其表内 role 不会自锁,故提前放行;
     * 仅当目标「不在白名单 且 当前 role=='admin'」时才可能移除,此时生效集合大小等于 1 即拒绝。
     */
    private void ensureNotRemovingLastEffectiveAdmin(String username, String currentRole) {
        if (adminUsernames.contains(username)) {
            return;
        }
        if (!ROLE_ADMIN.equals(currentRole)) {
            return;
        }
        if (effectiveAdminUsernames().size() <= 1) {
            throw new BusinessException("不能移除最后一个管理员");
        }
    }

    private Set<String> effectiveAdminUsernames() {
        Set<String> all = new HashSet<>(adminUsernames);
        List<String> dbAdmins = adminMapper.selectAdminUsernames();
        if (dbAdmins != null) {
            all.addAll(dbAdmins);
        }
        return all;
    }

    private long asLong(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }
}