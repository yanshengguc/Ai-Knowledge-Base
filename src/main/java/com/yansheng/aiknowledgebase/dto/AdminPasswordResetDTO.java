package com.yansheng.aiknowledgebase.dto;

import lombok.Getter;
import lombok.Setter;

/** 管理端重置密码入参(B-116) */
@Setter
@Getter
public class AdminPasswordResetDTO {

    private String password;
}