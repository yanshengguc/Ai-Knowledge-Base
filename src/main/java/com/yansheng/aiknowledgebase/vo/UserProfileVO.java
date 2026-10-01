package com.yansheng.aiknowledgebase.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 当前登录用户的轻量档案(GET /api/user/me)。
 * 仅回传前端渲染所需字段:是否管理员由配置白名单判定,不落库、不进 JWT。
 */
@Setter
@Getter
public class UserProfileVO {

    private long id;

    private String username;

    /** 是否为管理端用户(admin.usernames 白名单命中) */
    private boolean admin;
}