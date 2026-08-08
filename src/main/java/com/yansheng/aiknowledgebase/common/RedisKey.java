package com.yansheng.aiknowledgebase.common;

public class RedisKey {
    public static String knowledge(Long id){
        return "knowledge"+id;
    }
}
