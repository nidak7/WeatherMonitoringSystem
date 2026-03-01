package com.nidak.weatherpulse.exception;

import org.springframework.http.HttpStatus;

public class WeatherServiceException extends RuntimeException {

    private final HttpStatus status;

    public WeatherServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
