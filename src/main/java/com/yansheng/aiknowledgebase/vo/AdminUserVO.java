package com.yansheng.aiknowledgebase.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理端用户列表行。
 * 刻意不含 password:管理端只读视图不得把凭证哈希带出接口。
 */
@Setter
@Getter
public class AdminUserVO {

    private long id;

    private String username;

    private String nickname;
}