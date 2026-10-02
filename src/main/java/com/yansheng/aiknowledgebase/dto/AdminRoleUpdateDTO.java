package com.yansheng.aiknowledgebase.dto;

import lombok.Getter;
import lombok.Setter;

/** 管理端改角色入参(B-116):仅接受 admin/user */
@Setter
@Getter
public class AdminRoleUpdateDTO {

    private String role;
}