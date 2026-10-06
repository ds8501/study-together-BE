package com.studytogether.api.model.dto.response;

import java.time.Instant;

public record WorkspaceMemberResponse(
        SafeUserResponse user,
        String role,
        Instant joinedAt
) {}
