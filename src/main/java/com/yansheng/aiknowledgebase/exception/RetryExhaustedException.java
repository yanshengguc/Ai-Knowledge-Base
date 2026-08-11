package com.yansheng.aiknowledgebase.exception;

public class RetryExhaustedException extends RuntimeException{
    public  RetryExhaustedException(String message,Throwable cause){

        super(message,cause);

    }

}
