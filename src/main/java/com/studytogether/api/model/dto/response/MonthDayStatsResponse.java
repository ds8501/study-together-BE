package com.studytogether.api.model.dto.response;

public record MonthDayStatsResponse(
        String key,
        int dayNumber,
        String dayOfWeek,
        boolean active,
        boolean today,
        boolean future,
        int topicsStudied,
        int minutesStudied
) {
    public MonthDayStatsResponse(String key, int dayNumber, String dayOfWeek, boolean active, boolean today, boolean future) {
        this(key, dayNumber, dayOfWeek, active, today, future, 0, 0);
    }
}
