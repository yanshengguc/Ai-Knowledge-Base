package com.yansheng.aiknowledgebase.dto;

import lombok.Getter;
import lombok.Setter;

/** 管理端建号入参(B-116):role 省略时默认 user,仅接受 admin/user */
@Setter
@Getter
public class AdminUserCreateDTO {

    private String username;

    private String password;

    private String nickname;

    private String role;
}