package com.yansheng.aiknowledgebase.common;

public class RetryExhaustedException extends RuntimeException{
    public  RetryExhaustedException(String message,Throwable cause){

        super(message,cause);

    }

}
