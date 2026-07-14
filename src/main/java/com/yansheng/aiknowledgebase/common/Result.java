package com.yansheng.aiknowledgebase.common;


public class Result {
    private  int code;
    private  String message;
    private  Object  data;
    public Result(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static Result success(Object data) {
        return new Result(200, "success", data);
    }
    public static Result error(String message) {
     return  new Result(500, message, null);
    }
    public int getCode() {return code;
    };
    public String getMessage() { return message; }
    public Object getData() { return data; }
}
