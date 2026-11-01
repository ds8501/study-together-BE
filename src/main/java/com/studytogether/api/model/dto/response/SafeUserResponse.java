package com.studytogether.api.model.dto.response;

import java.time.Instant;

public record SafeUserResponse(
        Long id,
        String name,
        String email,
        String avatar,
        Instant createdAt
) {}
