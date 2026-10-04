package com.studytogether.api.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleStatus(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("error", Optional.ofNullable(error.getReason()).orElse("Request failed")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception error) {
        if (error instanceof DuplicateKeyException)
            return ResponseEntity.status(409).body(Map.of("error", "An account with that email already exists"));
        error.printStackTrace();
        return ResponseEntity.internalServerError().body(Map.of("error", "Something went wrong"));
    }
}
