package com.studytogether.api.model.dto.response;

import java.util.List;

public record StudyPlansResponse(
        List<StudyPlanResponse> plans
) {}
