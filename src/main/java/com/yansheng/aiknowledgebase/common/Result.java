package com.yansheng.aiknowledgebase.common;


public class Result<K> {
    private  int code;
    private  String message;
    private  Object  data;
    public Result(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static Result<K> success(Object data) {
        return new Result<K>(200, "success", data);
    }
    public static Result<K> error(String message) {
     return  new Result<K>(500, message, null);
    }
    public int getCode() {return code;
    };
    public String getMessage() { return message; }
    public Object getData() { return data; }
}
