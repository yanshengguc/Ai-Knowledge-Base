package com.yansheng.aiknowledgebase;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.yansheng.aiknowledgebase.mapper")
public class AiKnowledgeBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiKnowledgeBaseApplication.class, args);
    }

}
