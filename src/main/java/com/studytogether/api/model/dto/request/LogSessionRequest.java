package com.studytogether.api.model.dto.request;

public record LogSessionRequest(
        Long topicId,
        Integer durationMinutes,
        String notes,
        String timeZone
) {}
