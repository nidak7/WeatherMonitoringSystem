package com.nidak.weatherpulse.exception;

import com.nidak.weatherpulse.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WeatherServiceException.class)
    public ResponseEntity<ApiResponse<String>> handleWeatherServiceException(WeatherServiceException exception) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(ApiResponse.<String>of(exception.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleUnexpectedException(Exception exception) {
        return ResponseEntity
                .internalServerError()
                .body(ApiResponse.of("Unexpected server error", exception.getMessage()));
    }
}
