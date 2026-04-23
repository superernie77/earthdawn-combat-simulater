package com.earthdawn.controller;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(EntityNotFoundException ex) {
        log.warn("Not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "EntityNotFoundException", "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        log.warn("IllegalStateException: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "IllegalStateException", "message", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAll(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", ex.getClass().getSimpleName(), "message", ex.getMessage() != null ? ex.getMessage() : "null"));
    }
}
