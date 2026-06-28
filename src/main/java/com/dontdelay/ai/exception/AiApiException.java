package com.dontdelay.ai.exception;

import lombok.Getter;

@Getter
public class AiApiException extends RuntimeException {

    private final AiErrorCode errorCode;

    public AiApiException(AiErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AiApiException(AiErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
