package com.yansheng.aiknowledgebase.controller;

import com.yansheng.aiknowledgebase.common.Result;
import com.yansheng.aiknowledgebase.dto.ChatDTO;
import com.yansheng.aiknowledgebase.entity.ChatResponse;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.service.ChatService;
import com.yansheng.aiknowledgebase.utils.UserContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /** 单条消息最大字符数 */
    private static final int MAX_MESSAGE_LENGTH = 2000;
    /** 每用户每分钟最大请求数(LLM 调用花钱,防滥用) */
    private static final int MAX_PER_MINUTE = 10;

    private final ChatService chatService;
    private final RedisTemplate<String, Object> redisTemplate;

    public ChatController(ChatService chatService, RedisTemplate<String, Object> redisTemplate) {
        this.chatService = chatService;
        this.redisTemplate = redisTemplate;
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

        // 频率限制:每用户每分钟 MAX_PER_MINUTE 次(LLM 调用防刷)
        String rateKey = "chat:rate:" + userId;
        Long count = redisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(rateKey, 1, TimeUnit.MINUTES);
        }
        if (count != null && count > MAX_PER_MINUTE) {
            throw new BusinessException("请求太频繁,请稍后再试");
        }

        ChatResponse response = chatService.ask(userId, message);
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

        // 频率限制(与 chat 共用计数)
        String rateKey = "chat:rate:" + userId;
        Long count = redisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(rateKey, 1, TimeUnit.MINUTES);
        }
        if (count != null && count > MAX_PER_MINUTE) {
            throw new BusinessException("请求太频繁,请稍后再试");
        }

        SseEmitter emitter = new SseEmitter(120_000L);
        chatService.streamAsk(userId, message,
                token -> safeSend(emitter, SseEmitter.event().name("token").data(token)),
                refs -> {
                    safeSend(emitter, SseEmitter.event().name("refs").data(refs));
                    // 流结束:引用发完后关闭连接,前端 reader 收到 done
                    emitter.complete();
                });
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
