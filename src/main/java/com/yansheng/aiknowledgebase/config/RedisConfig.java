package com.yansheng.aiknowledgebase.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;


@Configuration
public class RedisConfig {


    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory factory) {


        RedisTemplate<String, Object> template = new RedisTemplate<>();

        template.setConnectionFactory(factory);


        ObjectMapper mapper = new ObjectMapper();

        mapper.registerModule(new JavaTimeModule());


        // 开启类型信息(白名单限定反序列化范围,防止任意类反序列化攻击面)
        // 覆盖本工程所有缓存值类型:com.yansheng.*(VO/SearchResult)与 java.util.*(ArrayList/LinkedHashMap)
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.yansheng.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .build();
        mapper.activateDefaultTyping(
                ptv,
                ObjectMapper.DefaultTyping.NON_FINAL
        );


        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(mapper);


        template.setKeySerializer(
                new StringRedisSerializer()
        );

        template.setValueSerializer(serializer);


        template.setHashKeySerializer(
                new StringRedisSerializer()
        );

        template.setHashValueSerializer(serializer);


        template.afterPropertiesSet();

        return template;
    }
}