package com.studytogether.api.model.dto.response;

public record LogSessionResponse(
        boolean ok,
        int currentStreak,
        int bestStreak,
        StudyStatsResponse stats
) {}
