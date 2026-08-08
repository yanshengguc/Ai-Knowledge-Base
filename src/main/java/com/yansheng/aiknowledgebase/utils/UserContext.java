package com.yansheng.aiknowledgebase.utils;

import com.yansheng.aiknowledgebase.entity.UserEntity;

public class UserContext {
    private static final ThreadLocal<UserEntity> THREAD_LOCAL = new ThreadLocal<>();

    public static void set(UserEntity userEntity) {
        THREAD_LOCAL.set(userEntity);
    }

    public static UserEntity get() {
        return THREAD_LOCAL.get();
    }

    public static Long getUserId() {
        UserEntity user = THREAD_LOCAL.get();
        return user == null ? null : user.getId();
    }

    public static void remove() {
        THREAD_LOCAL.remove();
    }
}
