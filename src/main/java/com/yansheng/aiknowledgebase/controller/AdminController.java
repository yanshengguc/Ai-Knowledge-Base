package com.yansheng.aiknowledgebase.controller;

import com.yansheng.aiknowledgebase.common.Result;
import com.yansheng.aiknowledgebase.service.AdminService;
import com.yansheng.aiknowledgebase.vo.AdminOverviewVO;
import com.yansheng.aiknowledgebase.vo.AdminUserPageVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端只读接口(2026-10 Sprint 3 MVP)。
 * 鉴权在 AdminService 内做(requireAdmin),Controller 不承载权限判断,避免两处逻辑漂移。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public Result<AdminOverviewVO> overview() {
        return Result.success(adminService.overview());
    }

    @GetMapping("/users")
    public Result<AdminUserPageVO> users(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) String keyword) {
        return Result.success(adminService.users(page, size, keyword));
    }
}