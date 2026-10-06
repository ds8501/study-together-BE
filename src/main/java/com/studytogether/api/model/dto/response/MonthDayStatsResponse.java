package com.studytogether.api.model.dto.response;

public record MonthDayStatsResponse(
        String key,
        int dayNumber,
        String dayOfWeek,
        boolean active,
        boolean today,
        boolean future
) {}
