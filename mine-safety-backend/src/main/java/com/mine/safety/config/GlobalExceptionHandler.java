package com.mine.safety.config;

import com.mine.safety.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一捕获并处理Controller层抛出的异常，返回标准化的错误响应
 *
 * 处理范围：
 *   - 运行时异常（RuntimeException）
 *   - 系统级异常（Exception）
 *   - 参数校验异常（MethodArgumentNotValidException）
 *   - 参数绑定异常（BindException）
 *
 * 返回格式：统一使用 ApiResponse<T> 封装
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理运行时异常
     * 捕获业务逻辑中抛出的RuntimeException及其子类
     *
     * @param e 运行时异常
     * @return 标准错误响应
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: {}", e.getMessage(), e);
        return ApiResponse.error(e.getMessage());
    }

    /**
     * 处理系统级异常
     * 捕获所有未被其他处理器处理的Exception
     *
     * @param e 系统异常
     * @return 标准错误响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return ApiResponse.error("系统内部错误: " + e.getMessage());
    }

    /**
     * 处理参数校验异常
     * 捕获@Valid注解触发的校验失败异常
     *
     * @param e 参数校验异常
     * @return 标准错误响应，包含所有校验错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return ApiResponse.error(400, message);
    }

    /**
     * 处理参数绑定异常
     * 捕获表单参数绑定失败异常
     *
     * @param e 参数绑定异常
     * @return 标准错误响应，包含所有绑定错误信息
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数绑定失败: {}", message);
        return ApiResponse.error(400, message);
    }
}
