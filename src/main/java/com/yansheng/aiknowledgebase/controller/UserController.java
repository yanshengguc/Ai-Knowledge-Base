package com.yansheng.aiknowledgebase.controller;

import com.yansheng.aiknowledgebase.common.Result;
import com.yansheng.aiknowledgebase.dto.LoginDTO;
import com.yansheng.aiknowledgebase.dto.UserRegisterDTO;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.service.AdminService;
import com.yansheng.aiknowledgebase.service.RateLimitService;
import com.yansheng.aiknowledgebase.service.UserService;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.UserProfileVO;
import com.yansheng.aiknowledgebase.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;
    private final RateLimitService rateLimitService;
    private final AdminService adminService;
    @Value("${register.enabled:true}")
    private boolean registerEnabled;
    public UserController(UserService userService, RateLimitService rateLimitService,
                          AdminService adminService) {
        this.userService = userService;
        this.rateLimitService = rateLimitService;
        this.adminService = adminService;
    }
    @GetMapping("/{id}")
    public Result getUserById(@PathVariable long id){
        // 越权防护(IDOR 修复):用户信息仅允许查自己,防注册用户名枚举
        UserEntity current = UserContext.get();
        if (current == null || current.getId() != id) {
            throw new BusinessException("权限不足");
        }
        UserVO userVO=userService.getUserById(id);
        return Result.success(userVO);

    }
    /** 当前登录用户档案:前端据此决定管理端导航与 /admin 路由是否可见(不落库,admin 来自配置白名单) */
    @GetMapping("/me")
    public Result<UserProfileVO> me(){
        UserEntity current = UserContext.get();
        if (current == null) {
            throw new BusinessException("登录已过期,请重新登录");
        }
        UserProfileVO vo = new UserProfileVO();
        vo.setId(current.getId());
        vo.setUsername(current.getUsername());
        vo.setAdmin(adminService.isAdmin(current.getUsername()));
        return Result.success(vo);
    }
    @PostMapping("/register")
    public Result<Void> register(@RequestBody UserRegisterDTO dto, HttpServletRequest request){
        // 注册开关:生产可关闭,防陌生人注册(个人知识库场景)
        if (!registerEnabled) {
            throw new BusinessException("注册已关闭");
        }
        // 防刷:注册按客户端 IP 限流(生产经 Nginx 代理,取 X-Forwarded-For 首段)
        rateLimitService.check("ip:" + clientIp(request), "register", 5);
        userService.register(dto);
        return Result.success();
    }
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginDTO dto){

       String token = userService.login(dto);
        return Result.success(token);
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}