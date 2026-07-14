package com.yansheng.aiknowledgebase.controller;

import com.yansheng.aiknowledgebase.service.HelloService;
import com.yansheng.aiknowledgebase.vo.HelloResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")

public class HelloController {
private final HelloService helloService;

    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }


    @GetMapping("/hello")
    public HelloResult hello(@RequestParam String name){
    return helloService.hello(name);
}
}
