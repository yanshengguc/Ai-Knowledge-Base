package com.yansheng.aiknowledgebase.handler;


import com.yansheng.aiknowledgebase.common.BusinessException;
import com.yansheng.aiknowledgebase.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e){

        return Result.error(e.getMessage());
    }


    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e){

        log.error("系统异常",e);

        return Result.error("系统异常，请稍后重试");
    }

}