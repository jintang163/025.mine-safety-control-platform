package com.mine.safety.dto;

import lombok.Data;

/**
 * API统一响应封装类
 * 所有REST API的返回结果都使用此类封装，确保响应格式一致
 *
 * 响应格式（JSON）：
 * {
 *   "code": 200,
 *   "message": "success",
 *   "data": { ... },
 *   "timestamp": 1700000000000
 * }
 *
 * 状态码约定：
 *   - 200: 成功
 *   - 400: 参数错误
 *   - 401: 未授权
 *   - 403: 禁止访问
 *   - 404: 资源不存在
 *   - 500: 服务器内部错误
 *
 * @param <T> 响应数据的类型
 */
@Data
public class ApiResponse<T> {

    /**
     * 状态码
     * 200表示成功，其他表示错误
     */
    private int code;

    /**
     * 响应消息
     * 成功时为"success"，错误时为错误描述
     */
    private String message;

    /**
     * 响应数据
     * 成功时返回具体数据，错误时为null
     */
    private T data;

    /**
     * 响应时间戳（毫秒）
     * 自动设置为当前系统时间
     */
    private long timestamp;

    /**
     * 默认构造函数
     * 自动设置时间戳
     */
    public ApiResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 全参数构造函数
     *
     * @param code    状态码
     * @param message 响应消息
     * @param data    响应数据
     */
    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 成功响应（带数据）
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    /**
     * 成功响应（无数据）
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(200, "success", null);
    }

    /**
     * 错误响应（默认500状态码）
     *
     * @param message 错误消息
     * @param <T>     数据类型
     * @return 错误响应
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, message, null);
    }

    /**
     * 错误响应（自定义状态码）
     *
     * @param code    错误状态码
     * @param message 错误消息
     * @param <T>     数据类型
     * @return 错误响应
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
