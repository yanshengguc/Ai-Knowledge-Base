package com.yansheng.aiknowledgebase.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * B-119:防止「作为 HTTP 响应体被 Jackson 序列化的 Throwable」带出完整堆栈(类名/文件名/行号)。
 *
 * 背景:{@code /api/mcp-endpoint} 由 Spring AI 注册为函数式端点(RouterFunction),
 * 错误出口用 {@code ServerResponse.badRequest().body(new McpError(...))} 正常返回,
 * 而 {@code McpError} 继承 {@code RuntimeException}。Spring MVC 用应用的 ObjectMapper
 * 序列化该响应体时,Jackson 默认按 Throwable 输出 {@code stackTrace} 数组
 * (含 className / fileName / lineNumber)→ 内部结构信息泄露,且不经过 @RestControllerAdvice。
 *
 * 本定制只改变「Throwable 被当作响应体序列化」的输出:剥离 stackTrace / suppressed / cause,
 * 仅保留面向客户端的 message。项目内唯一把 Throwable 直接作为响应体的位置就是上述 MCP 端点;
 * 普通 API 的异常由 GlobalExceptionHandler 统一处理(返回 Result 对象,不经此处),行为不变。
 */
@Configuration
public class SafeThrowableSerializationConfig {

    @Bean
    public SimpleModule safeThrowableModule() {
        SimpleModule module = new SimpleModule("b119-safe-throwable");
        module.addSerializer(Throwable.class, new JsonSerializer<Throwable>() {
            @Override
            public void serialize(Throwable value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                String message = value.getMessage();
                gen.writeStartObject();
                gen.writeStringField("message", (message == null || message.isBlank()) ? "Internal error" : message);
                gen.writeEndObject();
            }
        });
        return module;
    }
}