package com.studytogether.api.model.dto.response;

import java.util.List;

public record StudyStatsResponse(
        int currentStreak,
        int bestStreak,
        int totalDaysStudiedThisMonth,
        String monthName,
        int year,
        List<MonthDayStatsResponse> month,
        List<WeekDayStatsResponse> week
) {}
