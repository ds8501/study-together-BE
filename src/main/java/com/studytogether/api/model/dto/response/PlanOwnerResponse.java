package com.studytogether.api.model.dto.response;

public record PlanOwnerResponse(
        Long id,
        String name,
        String avatar,
        String email
) {}
