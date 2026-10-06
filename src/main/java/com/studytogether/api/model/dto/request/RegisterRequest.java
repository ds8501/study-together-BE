package com.studytogether.api.model.dto.request;

public record RegisterRequest(
        String name,
        String email,
        String password,
        String workspaceName,
        String inviteCode
) {}
