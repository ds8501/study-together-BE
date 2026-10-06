package com.studytogether.api.model.dto.response;

import java.time.Instant;
import java.util.List;

public record StudyPlanResponse(
        Long id,
        Long workspaceId,
        Long ownerId,
        String name,
        String description,
        Instant createdAt,
        PlanOwnerResponse owner,
        List<CategoryResponse> categories,
        List<TopicResponse> topics
) {}

