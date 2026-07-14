package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.common.Result;
import com.yansheng.aiknowledgebase.service.HelloService;
import org.springframework.stereotype.Service;

@Service
public class HelloServiceImpl implements HelloService {
    @Override
     public Result hello(String name) {
     return   Result.success(name);

}}
