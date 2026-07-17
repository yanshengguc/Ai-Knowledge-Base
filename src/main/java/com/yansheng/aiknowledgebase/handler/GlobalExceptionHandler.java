package com.yansheng.aiknowledgebase.handler;


import com.yansheng.aiknowledgebase.common.BusinessException;
import com.yansheng.aiknowledgebase.common.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public Result businessException(BusinessException e){
        return  Result.error(e.getMessage());
    }
}
