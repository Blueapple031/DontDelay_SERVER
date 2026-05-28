package com.dontdelay.exam.exception;

import org.springframework.http.HttpStatus;

public enum ExamErrorCode {
    INVALID_FILE(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    EXAM_DISABLED(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    ExamErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
