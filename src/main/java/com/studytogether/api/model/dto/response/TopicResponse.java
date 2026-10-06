package com.studytogether.api.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.studytogether.api.model.entity.TopicEntity;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TopicResponse(
        Long id,
        Long categoryId,
        Long studyPlanId,
        Long ownerId,
        String title,
        String description,
        Integer dayNumber,
        int order,
        TopicEntity.Difficulty difficulty,
        TopicEntity.Priority priority,
        Double estimatedHours,
        TopicEntity.Status status,
        Instant createdAt,
        Instant updatedAt,
        CategoryResponse category
) {
    public static TopicResponse fromEntity(TopicEntity t, CategoryResponse category) {
        if (t == null) return null;
        return new TopicResponse(
                t.id(),
                t.categoryId(),
                t.studyPlanId(),
                t.ownerId(),
                t.title(),
                t.description(),
                t.dayNumber(),
                t.order(),
                t.difficulty(),
                t.priority(),
                t.estimatedHours(),
                t.status(),
                t.createdAt(),
                t.updatedAt(),
                category
        );
    }

    public static TopicResponse fromEntity(TopicEntity t) {
        return fromEntity(t, null);
    }
}
