package com.studytogether.api.model.dto.response;

import com.studytogether.api.model.entity.CategoryEntity;

public record CategoryResponse(
        Long id,
        Long studyPlanId,
        String name,
        String description,
        String icon,
        String color,
        int order
) {
    public static CategoryResponse fromEntity(CategoryEntity entity) {
        if (entity == null) {
            return null;
        }
        return new CategoryResponse(
                entity.id(),
                entity.studyPlanId(),
                entity.name(),
                entity.description(),
                entity.icon(),
                entity.color(),
                entity.order()
        );
    }
}
