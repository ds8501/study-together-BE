package com.studytogether.api.model.dto.request;

import com.studytogether.api.model.entity.TopicEntity;

public record CreateTopicRequest(
        String title,
        String description,
        Long categoryId,
        String categoryName,
        Integer dayNumber,
        Double estimatedHours,
        TopicEntity.Difficulty difficulty,
        TopicEntity.Priority priority,
        TopicEntity.Status status
) {}
