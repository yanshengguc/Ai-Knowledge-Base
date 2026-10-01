package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.AdminMapper;
import com.yansheng.aiknowledgebase.service.AdminService;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.AdminOverviewVO;
import com.yansheng.aiknowledgebase.vo.AdminUserPageVO;
import com.yansheng.aiknowledgebase.vo.AdminUserVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
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

    private final Set<String> adminUsernames;
    private final AdminMapper adminMapper;

    public AdminServiceImpl(@Value("${admin.usernames:}") String rawAdminUsernames,
                            AdminMapper adminMapper) {
        this.adminUsernames = Arrays.stream(rawAdminUsernames.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.adminMapper = adminMapper;
    }

    @Override
    public boolean isAdmin(String username) {
        // 空配置 = 无人是管理员(安全默认:忘配环境变量时管理端整体关闭,而不是全开)
        return username != null && adminUsernames.contains(username);
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
        List<AdminUserVO> list = adminMapper.selectUsers(safeKeyword, safeSize, (safePage - 1) * safeSize);
        vo.setList(list);
        return vo;
    }

    private long asLong(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }
}