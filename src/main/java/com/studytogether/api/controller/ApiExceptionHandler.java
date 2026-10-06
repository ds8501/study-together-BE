package com.studytogether.api.controller;

import java.util.Optional;

import com.studytogether.api.model.dto.response.ErrorResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleStatus(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(new ErrorResponse(Optional.ofNullable(error.getReason()).orElse("Request failed")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception error) {
        if (error instanceof DuplicateKeyException)
            return ResponseEntity.status(409).body(new ErrorResponse("An account with that email already exists"));
        error.printStackTrace();
        return ResponseEntity.internalServerError().body(new ErrorResponse("Something went wrong"));
    }
}
