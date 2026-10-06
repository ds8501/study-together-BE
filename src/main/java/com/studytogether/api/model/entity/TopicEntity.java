package com.studytogether.api.model.entity;

import com.studytogether.api.util.EntityHelper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import org.springframework.jdbc.core.RowMapper;

public record TopicEntity(
        Long id,
        Long categoryId,
        Long studyPlanId,
        Long ownerId,
        String title,
        String description,
        Integer dayNumber,
        int order,
        Difficulty difficulty,
        Priority priority,
        Double estimatedHours,
        Status status,
        Instant createdAt,
        Instant updatedAt) {

    public enum Difficulty { EASY, MEDIUM, HARD }
    public enum Priority { LOW, MEDIUM, HIGH }
    public enum Status { NOT_STARTED, IN_PROGRESS, COMPLETED }

    public static final RowMapper<TopicEntity> ROW_MAPPER = (rs, rowNum) -> fromResultSet(rs);

    public static TopicEntity fromResultSet(ResultSet rs) throws SQLException {
        String diffStr = EntityHelper.getString(rs, "difficulty");
        String prioStr = EntityHelper.getString(rs, "priority");
        String statStr = EntityHelper.getString(rs, "status");
        Integer order = EntityHelper.getInteger(rs, "order");

        Difficulty diff = Difficulty.MEDIUM;
        if (diffStr != null) {
            try { diff = Difficulty.valueOf(diffStr); } catch (IllegalArgumentException ignored) {}
        }
        Priority prio = Priority.MEDIUM;
        if (prioStr != null) {
            try { prio = Priority.valueOf(prioStr); } catch (IllegalArgumentException ignored) {}
        }
        Status stat = Status.NOT_STARTED;
        if (statStr != null) {
            try { stat = Status.valueOf(statStr); } catch (IllegalArgumentException ignored) {}
        }

        return new TopicEntity(
                EntityHelper.getLong(rs, "id"),
                EntityHelper.getLong(rs, "categoryId"),
                EntityHelper.getLong(rs, "studyPlanId"),
                EntityHelper.getLong(rs, "ownerId"),
                EntityHelper.getString(rs, "title"),
                EntityHelper.getString(rs, "description"),
                EntityHelper.getInteger(rs, "dayNumber"),
                order != null ? order : 0,
                diff,
                prio,
                EntityHelper.getDouble(rs, "estimatedHours"),
                stat,
                EntityHelper.getInstant(rs, "createdAt"),
                EntityHelper.getInstant(rs, "updatedAt")
        );
    }
}
