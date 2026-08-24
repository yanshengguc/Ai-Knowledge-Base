package com.yansheng.aiknowledgebase.service;

import java.util.function.BiConsumer;

public interface GenerationService {
     String generate(String prompt);

     /** 流式生成:逐 token 返回(SSE 打字机效果) */
     reactor.core.publisher.Flux<String> generateStream(String prompt);

     /** 同步生成,完成后回调 (promptTokens, completionTokens) 供用量统计 */
     String generate(String prompt, BiConsumer<Long, Long> onUsage);

     /** 流式生成,流结束时回调 (promptTokens, completionTokens) 供用量统计 */
     reactor.core.publisher.Flux<String> generateStream(String prompt, BiConsumer<Long, Long> onUsage);
}
