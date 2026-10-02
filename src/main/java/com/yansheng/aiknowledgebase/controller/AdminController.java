package com.yansheng.aiknowledgebase.controller;

import com.yansheng.aiknowledgebase.common.Result;
import com.yansheng.aiknowledgebase.dto.AdminPasswordResetDTO;
import com.yansheng.aiknowledgebase.dto.AdminRoleUpdateDTO;
import com.yansheng.aiknowledgebase.dto.AdminUserCreateDTO;
import com.yansheng.aiknowledgebase.service.AdminService;
import com.yansheng.aiknowledgebase.vo.AdminOverviewVO;
import com.yansheng.aiknowledgebase.vo.AdminUserPageVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端读写接口(B-116 起含写操作)。
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

    /** 管理员建号(username/password/nickname?/role?,role 默认 user) */
    @PostMapping("/users")
    public Result<Void> createUser(@RequestBody AdminUserCreateDTO dto) {
        adminService.createUser(dto);
        return Result.success();
    }

    /** 改角色(body: { "role": "admin" | "user" }) */
    @PatchMapping("/users/{id}/role")
    public Result<Void> updateUserRole(@PathVariable long id, @RequestBody AdminRoleUpdateDTO dto) {
        adminService.updateUserRole(id, dto.getRole());
        return Result.success();
    }

    /** 重置密码(body: { "password": "..." }) */
    @PutMapping("/users/{id}/password")
    public Result<Void> resetUserPassword(@PathVariable long id, @RequestBody AdminPasswordResetDTO dto) {
        adminService.resetUserPassword(id, dto.getPassword());
        return Result.success();
    }

    /** 删除用户 */
    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable long id) {
        adminService.deleteUser(id);
        return Result.success();
    }
}