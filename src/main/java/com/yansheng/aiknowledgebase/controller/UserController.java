package com.yansheng.aiknowledgebase.controller;

import com.yansheng.aiknowledgebase.common.Result;
import com.yansheng.aiknowledgebase.dto.LoginDTO;
import com.yansheng.aiknowledgebase.dto.UserRegisterDTO;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.service.UserService;
import com.yansheng.aiknowledgebase.vo.UserVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;
    public UserController(UserService userService) {
        this.userService = userService;
    }
    @GetMapping("/{id}")
    public Result getUserById(@PathVariable long id){
        UserVO userVO=userService.getUserById(id);
        return Result.success(userVO);

    }
    @PostMapping("/register")
    public Result<Void> register(@RequestBody UserRegisterDTO dto){

        userService.register(dto);
        return Result.success();
    }
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginDTO dto){

        userService.login(dto);
        return Result.success("success");
    }
}
