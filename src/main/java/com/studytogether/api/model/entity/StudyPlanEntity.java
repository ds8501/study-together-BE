package com.studytogether.api.model.entity;

import com.studytogether.api.util.EntityHelper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import org.springframework.jdbc.core.RowMapper;

public record StudyPlanEntity(
        Long id,
        Long workspaceId,
        Long ownerId,
        String name,
        String description,
        Instant createdAt) {

    public static final RowMapper<StudyPlanEntity> ROW_MAPPER = (rs, rowNum) -> fromResultSet(rs);

    public static StudyPlanEntity fromResultSet(ResultSet rs) throws SQLException {
        return new StudyPlanEntity(
                EntityHelper.getLong(rs, "id"),
                EntityHelper.getLong(rs, "workspaceId"),
                EntityHelper.getLong(rs, "ownerId"),
                EntityHelper.getString(rs, "name"),
                EntityHelper.getString(rs, "description"),
                EntityHelper.getInstant(rs, "createdAt")
        );
    }
}
