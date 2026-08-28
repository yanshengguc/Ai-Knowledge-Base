package com.yansheng.aiknowledgebase.config;



import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class HttpClientConfig {

    /**
     * RestClient 工厂:连接超时 15s / 读超时 90s(LLM 生成慢,不能按普通接口的秒级设)。
     * classpath 引入 httpclient5 后,ClientHttpRequestFactories 自动选 Apache 工厂(带连接池),
     * Agent 循环的多次非流式 call 复用 TCP/TLS 连接,省掉每次握手的数百毫秒。
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(15))
                .withReadTimeout(Duration.ofSeconds(90));

        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings));
    }
}