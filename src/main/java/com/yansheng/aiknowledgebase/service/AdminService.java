package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.vo.AdminOverviewVO;
import com.yansheng.aiknowledgebase.vo.AdminUserPageVO;

/**
 * 管理端只读能力。
 *
 * 鉴权采用白名单制(admin.usernames 配置),不引入角色字段、不改表:
 * 个人知识库场景下管理端只有"站内运营视图"一个用途,不值得为它做一次 DDL 迁移。
 * 每个对外方法内部先 requireAdmin(),把校验收敛到唯一入口,避免调用方漏判。
 */
public interface AdminService {

    /** 用户名是否命中管理端白名单 */
    boolean isAdmin(String username);

    /** 未命中白名单直接抛业务异常(HTTP 200 + code=500,与既有 BusinessException 口径一致) */
    void requireAdmin();

    /** 全站只读概览 */
    AdminOverviewVO overview();

    /** 用户列表(服务端分页 + 关键词,不回传 password) */
    AdminUserPageVO users(int page, int size, String keyword);
}