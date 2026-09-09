package com.aifishing.common.exception;

public class BadRequestException extends RuntimeException {

    private final String code;

    public BadRequestException(String message) {
        this("VALIDATION_ERROR", message);
    }

    public BadRequestException(String code, String message) {
        super(message);
        this.code = code == null || code.isBlank() ? "VALIDATION_ERROR" : code;
    }

    public String getCode() {
        return code;
    }
}
