package com.yansheng.aiknowledgebase.handler;


import com.yansheng.aiknowledgebase.exception.BusinessException;
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

    /** @Valid 校验失败:给出首个字段的明确提示(而非误导性的"系统异常"),契约仍为 HTTP 200 + body code:500 */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public Result handleValidation(org.springframework.web.bind.MethodArgumentNotValidException e){
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(org.springframework.validation.FieldError::getDefaultMessage)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", msg);
        return Result.error(msg);
    }


    /**
     * 路径参数类型不匹配(如 /api/knowledge/abc):返回明确的"参数格式不正确",
     * 而不是误导性的"系统异常"
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public Result handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e){
        return Result.error("参数格式不正确");
    }

    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e){

        log.error("系统异常",e);

        return Result.error("系统异常，请稍后重试");
    }

}
