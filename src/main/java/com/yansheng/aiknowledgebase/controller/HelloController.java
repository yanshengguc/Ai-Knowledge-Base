package com.yansheng.aiknowledgebase.controller;

import com.yansheng.aiknowledgebase.vo.HelloResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")

public class HelloController {
    @GetMapping("/hello")
    public HelloResult hello(){

        return  new HelloResult (
                200,
                "success",
                "Hello World");
    }

}
