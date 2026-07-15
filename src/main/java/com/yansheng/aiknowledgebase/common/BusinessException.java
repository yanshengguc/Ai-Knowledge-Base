package com.yansheng.aiknowledgebase.common;

import com.yansheng.aiknowledgebase.handler.GlobalExceptionHandler;

public class BusinessException extends RuntimeException{
    public  BusinessException(String message){
  super(message);
    }
}
