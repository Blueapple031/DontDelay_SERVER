package com.dontdelay.exam.exception;

import lombok.Getter;

import java.util.Map;

@Getter
public class ExamApiException extends RuntimeException {

    private final ExamErrorCode errorCode;
    private final Map<String, Object> details;

    public ExamApiException(ExamErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ExamApiException(ExamErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }
}
