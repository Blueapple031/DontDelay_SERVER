package com.dontdelay.ai.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum AiErrorCode {
    LLM_UNAVAILABLE(HttpStatus.BAD_GATEWAY),
    AI_DISABLED(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    AiErrorCode(HttpStatus status) {
        this.status = status;
    }
}
