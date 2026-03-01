package com.nidak.weatherpulse.dto;

import java.time.LocalDateTime;

public record ApiResponse<T>(String message, T data, LocalDateTime timestamp) {

    public static <T> ApiResponse<T> of(String message, T data) {
        return new ApiResponse<>(message, data, LocalDateTime.now());
    }
}
