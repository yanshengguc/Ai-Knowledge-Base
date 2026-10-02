package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.dto.AdminUserCreateDTO;
import com.yansheng.aiknowledgebase.vo.AdminOverviewVO;
import com.yansheng.aiknowledgebase.vo.AdminUserPageVO;

/**
 * 管理端读写能力(B-116 起含写操作)。
 *
 * 鉴权:既有白名单(admin.usernames)叠加表内 user.role='admin' 二值模型,做平滑过渡——
 * 白名单用户天然是管理员(快速路径,不查库),表内 role='admin' 由管理端写操作授予/撤销。
 * 每个对外方法内部先 requireAdmin(),把校验收敛到唯一入口,避免调用方漏判。
 * 写操作另叠六条安全护栏(自我操作 / 最后一个生效管理员 / 删除前置 / role 白名单 / 写前鉴权 / 不回传 password)。
 */
public interface AdminService {

    /** 用户名是否为管理员:命中白名单快速返回;否则查库判 role == 'admin' */
    boolean isAdmin(String username);

    /** 非管理员直接抛业务异常(HTTP 200 + code=500,与既有 BusinessException 口径一致) */
    void requireAdmin();

    /** 全站只读概览 */
    AdminOverviewVO overview();

    /** 用户列表(服务端分页 + 关键词,不回传 password) */
    AdminUserPageVO users(int page, int size, String keyword);

    /** 管理员建号:username 非空唯一,password BCrypt 后入库,nickname 可空,role 默认 user */
    void createUser(AdminUserCreateDTO dto);

    /** 改角色:仅接受 admin/user;禁止操作自己;不得移除最后一个生效管理员 */
    void updateUserRole(long id, String role);

    /** 重置密码:禁止操作自己;password 非空 */
    void resetUserPassword(long id, String password);

    /** 删除用户:禁止删除自己;名下有 knowledge/knowledge_file 时拒绝;不得移除最后一个生效管理员 */
    void deleteUser(long id);
}