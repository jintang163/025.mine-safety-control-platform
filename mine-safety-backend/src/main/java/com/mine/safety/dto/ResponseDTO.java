package com.mine.safety.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResponseDTO<T> {

    private int code;

    private String message;

    private T data;

    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    private boolean success;

    public static <T> ResponseDTO<T> success() {
        return ResponseDTO.<T>builder()
                .code(200)
                .message("success")
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ResponseDTO<T> success(T data) {
        return ResponseDTO.<T>builder()
                .code(200)
                .message("success")
                .data(data)
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ResponseDTO<T> success(String message, T data) {
        return ResponseDTO.<T>builder()
                .code(200)
                .message(message)
                .data(data)
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ResponseDTO<T> error(String message) {
        return ResponseDTO.<T>builder()
                .code(500)
                .message(message)
                .success(false)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ResponseDTO<T> error(int code, String message) {
        return ResponseDTO.<T>builder()
                .code(code)
                .message(message)
                .success(false)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ResponseDTO<T> error(String message, T data) {
        return ResponseDTO.<T>builder()
                .code(500)
                .message(message)
                .data(data)
                .success(false)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
