package com.yansheng.aiknowledgebase.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 文档处理专用线程池。
 * 原因:上传后的解析/切片/向量化是 IO 密集型长任务(含多次外部网络调用),
 * 若用 CompletableFuture.runAsync 默认的 ForkJoinPool.commonPool(线程数≈CPU核数-1),
 * 多个并发上传会互相抢线程,导致状态长期 PROCESSING。
 */
@Configuration
public class AsyncConfig {

    @Bean("docProcessExecutor")
    public Executor docProcessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("doc-process-");
        // 队列满时由调用线程执行:上传场景宁可慢,不可丢任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
