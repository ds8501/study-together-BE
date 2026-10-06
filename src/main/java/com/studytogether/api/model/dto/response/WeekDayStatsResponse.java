package com.studytogether.api.model.dto.response;

public record WeekDayStatsResponse(
        String key,
        String label,
        boolean active,
        boolean today
) {}
