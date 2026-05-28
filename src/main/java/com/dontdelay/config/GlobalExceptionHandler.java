package com.dontdelay.config;

import com.dontdelay.exam.exception.ExamApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ExamApiException.class)
    public ResponseEntity<Map<String, Object>> handleExamApiException(ExamApiException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getErrorCode().name());
        body.put("message", ex.getMessage());
        if (ex.getDetails() != null) {
            body.put("details", ex.getDetails());
        }
        return ResponseEntity.status(ex.getErrorCode().getStatus()).body(body);
    }
}
