package com.dontdelay.exam.service;

public class ExtractionException extends RuntimeException {

    private final String errorCode;

    public ExtractionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ExtractionException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
