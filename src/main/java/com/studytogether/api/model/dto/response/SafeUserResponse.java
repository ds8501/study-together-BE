package com.studytogether.api.model.dto.response;

public record SafeUserResponse(
        Long id,
        String name,
        String email,
        String avatar
) {}
