package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.service.HelloService;
import com.yansheng.aiknowledgebase.vo.HelloResult;
import org.springframework.stereotype.Service;

@Service
public class HelloServiceImpl implements HelloService {
    @Override
     public HelloResult hello(String name) {
        return new  HelloResult(200, "success", name);
}}
