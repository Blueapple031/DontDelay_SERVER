package com.dontdelay.config;

import com.dontdelay.exam.exception.ExamApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "요청 값이 올바르지 않습니다.";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "VALIDATION_FAILED");
        body.put("message", message);
        return ResponseEntity.badRequest().body(body);
    }

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
