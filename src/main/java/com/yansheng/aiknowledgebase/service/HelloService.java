package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.controller.HelloController;
import com.yansheng.aiknowledgebase.service.impl.HelloServiceImpl;
import com.yansheng.aiknowledgebase.vo.HelloResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

public interface HelloService {
   HelloResult hello(String name) ;
    }

