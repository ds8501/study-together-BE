package com.studytogether.api.model.dto.request;

import com.studytogether.api.model.entity.TopicEntity;

public record UpdateTopicRequest(
        String title,
        String description,
        Long categoryId,
        String categoryName,
        Integer dayNumber,
        Double estimatedHours,
        Integer order,
        TopicEntity.Difficulty difficulty,
        TopicEntity.Priority priority,
        TopicEntity.Status status
) {}
