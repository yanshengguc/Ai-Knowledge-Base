package com.yansheng.aiknowledgebase.service;

public interface GenerationService {
     String generate(String prompt);

     /** 流式生成:逐 token 返回(SSE 打字机效果) */
     reactor.core.publisher.Flux<String> generateStream(String prompt);
}
