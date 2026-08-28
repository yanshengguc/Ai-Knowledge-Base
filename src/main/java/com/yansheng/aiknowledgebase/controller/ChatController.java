package com.yansheng.aiknowledgebase.controller;

import com.yansheng.aiknowledgebase.common.Result;
import com.yansheng.aiknowledgebase.dto.ChatDTO;
import com.yansheng.aiknowledgebase.entity.ChatResponse;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.service.ChatService;
import com.yansheng.aiknowledgebase.service.ChatQuotaService;
import com.yansheng.aiknowledgebase.service.RateLimitService;
import com.yansheng.aiknowledgebase.utils.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /** 单条消息最大字符数 */
    private static final int MAX_MESSAGE_LENGTH = 2000;
    /** 每用户每分钟最大请求数(LLM 调用花钱,防滥用) */
    private static final int MAX_PER_MINUTE = 10;

    private final ChatService chatService;
    private final RateLimitService rateLimitService;
    private final ChatQuotaService chatQuotaService;

    /** SSE 超时(毫秒):DeepSeek 长回答可能超过 2 分钟,部署后可按需调大 */
    @org.springframework.beans.factory.annotation.Value("${chat.sse-timeout-ms:300000}")
    private long sseTimeoutMs;

    public ChatController(ChatService chatService, RateLimitService rateLimitService,
                          ChatQuotaService chatQuotaService) {
        this.chatService = chatService;
        this.rateLimitService = rateLimitService;
        this.chatQuotaService = chatQuotaService;
    }

    /** 多轮对话:结合会话历史 + 知识库检索回答(需登录,会话按用户隔离) */
    @PostMapping
    public Result<ChatResponse> chat(@RequestBody ChatDTO dto) {
        Long userId = UserContext.getUserId();

        // 输入校验:消息非空 + 长度上限
        String message = dto.getMessage();
        if (message == null || message.isBlank()) {
            throw new BusinessException("消息不能为空");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException("消息过长,请控制在" + MAX_MESSAGE_LENGTH + "字以内");
        }

        // 频率限制:每用户每分钟 MAX_PER_MINUTE 次(独立组件,防刷)
        rateLimitService.check(userId, "chat", MAX_PER_MINUTE);

        // 每日配额:限流挡突发,配额挡"脚本全天匀速烧 key"(豁免用户不限)
        chatQuotaService.check(userId, UserContext.getUsername());

        ChatResponse response = chatService.ask(userId, message, dto.isEnableWebSearch());
        return Result.success(response);
    }

    /** 流式多轮对话(SSE 打字机效果):token 事件 + refs 事件(引用来源) */
    @PostMapping("/stream")
    public SseEmitter stream(@RequestBody ChatDTO dto) {
        Long userId = UserContext.getUserId();

        // 输入校验(与 chat 相同)
        String message = dto.getMessage();
        if (message == null || message.isBlank()) {
            throw new BusinessException("消息不能为空");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException("消息过长,请控制在" + MAX_MESSAGE_LENGTH + "字以内");
        }

        // 频率限制(独立组件,与 chat 共用计数)
        rateLimitService.check(userId, "chat", MAX_PER_MINUTE);

        // 每日配额(与 chat 同口径,两条入口都不漏)
        chatQuotaService.check(userId, UserContext.getUsername());

        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        chatService.streamAsk(userId, message,
                token -> safeSend(emitter, SseEmitter.event().name("token").data(token)),
                refs -> {
                    safeSend(emitter, SseEmitter.event().name("refs").data(refs));
                    // 流结束:引用发完后关闭连接,前端 reader 收到 done
                    emitter.complete();
                },
                dto.isEnableWebSearch());
        emitter.onCompletion(emitter::complete);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());
        return emitter;
    }

    private void safeSend(SseEmitter emitter, SseEmitter.SseEventBuilder builder) {
        try {
            emitter.send(builder);
        } catch (Exception ignored) {
            // 连接已断开,忽略后续推送
        }
    }

    /** 读取会话历史(前端刷新后恢复多轮上下文) */
    @GetMapping("/history")
    public Result<java.util.List<java.util.Map<String, String>>> history() {
        Long userId = UserContext.getUserId();
        return Result.success(chatService.history(userId));
    }

    /** 清空当前用户的会话历史 */
    @PostMapping("/clear")
    public Result<Void> clear() {
        chatService.clear(UserContext.getUserId());
        return Result.success();
    }
}
