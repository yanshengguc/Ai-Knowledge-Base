package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.TokenUsageMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 聊天每日配额(成本治理第三层:记账 token_usage + 限流 RateLimitService + 本组件防"日烧")。
 * 场景:公开演示账号/注册开放的窗口期,10 次/分的限流只挡突发,挡不住脚本全天匀速烧 API 余额。
 * 按 token 计而非按次:一次 Agent 对话最多 5 轮 ReAct 工具循环,每轮都是一次 LLM 调用,按次会低估。
 * 数据源直接复用 token_usage 的当日聚合——记账即事实源,避免 Redis 计数与 DB 双写漂移。
 */
@Service
public class ChatQuotaService {

    private final TokenUsageMapper tokenUsageMapper;
    private final boolean enabled;
    private final long tokenLimit;
    private final Set<String> exemptUsers;

    public ChatQuotaService(TokenUsageMapper tokenUsageMapper,
                            @Value("${chat.daily-quota.enabled:true}") boolean enabled,
                            @Value("${chat.daily-quota.token-limit:100000}") long tokenLimit,
                            @Value("${chat.daily-quota.exempt-users:}") String exemptUsers) {
        this.tokenUsageMapper = tokenUsageMapper;
        this.enabled = enabled;
        this.tokenLimit = tokenLimit;
        this.exemptUsers = Arrays.stream(exemptUsers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /** 当日 chat token 用量达到上限即拒绝新对话;豁免用户(作者本人)与开关关闭时跳过 */
    public void check(Long userId, String username) {
        if (!enabled || userId == null) {
            return;
        }
        if (username != null && exemptUsers.contains(username)) {
            return;
        }
        Map<String, Object> summary = tokenUsageMapper.selectChatSummaryByUserId(userId);
        Number today = summary == null ? null : (Number) summary.get("todayTokens");
        if (today != null && today.longValue() >= tokenLimit) {
            throw new BusinessException("今日体验额度已用完,请明天再来");
        }
    }
}
