package com.yansheng.aiknowledgebase.utils;

import com.yansheng.aiknowledgebase.exception.NonRetryableException;
import com.yansheng.aiknowledgebase.exception.RetryExhaustedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.concurrent.Callable;
@Component
public class HttpRetryUtil {
    public <T> T executeWithRetry(Callable<T> callable, int maxRetries) throws Exception {

        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                return callable.call();  // 成功直接返回，循环结束
            } catch (HttpServerErrorException e) {
                // 500 一类：服务器错误
                attempt++;
                if (attempt >= maxRetries) {
                    throw new RetryExhaustedException("重试" + maxRetries + "次后仍失败：服务器错误", e);
                }
                // 否则什么都不用做，while 循环自然进入下一轮
            } catch (HttpClientErrorException.TooManyRequests e) {
                // 429：限流，需要延迟
                attempt++;
                if (attempt >= maxRetries) {
                    throw new RetryExhaustedException("重试" + maxRetries + "次后仍失败：限流", e);
                }
                Thread.sleep(1000L * attempt);  // 简单的递增延迟，也可以用指数退避 2^attempt
            } catch (HttpClientErrorException e) {
                // 其他 4xx：不重试，直接抛
                throw new NonRetryableException("请求参数/认证错误，不重试", e);
            }
        }
        // 理论上走不到这里，因为循环内部次数用完就已经 throw 了
        throw new IllegalStateException("不应该到达这里");
    }
}
