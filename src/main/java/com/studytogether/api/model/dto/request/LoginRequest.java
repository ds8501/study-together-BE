package com.studytogether.api.model.dto.request;

public record LoginRequest(
        String email,
        String password
) {}
