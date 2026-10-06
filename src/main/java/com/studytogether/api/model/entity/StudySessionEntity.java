package com.studytogether.api.model.entity;

import com.studytogether.api.util.EntityHelper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import org.springframework.jdbc.core.RowMapper;

public record StudySessionEntity(
        Long id,
        Long userId,
        Long topicId,
        Instant date,
        int durationMinutes,
        String notes) {

    public static final RowMapper<StudySessionEntity> ROW_MAPPER = (rs, rowNum) -> fromResultSet(rs);

    public static StudySessionEntity fromResultSet(ResultSet rs) throws SQLException {
        Integer duration = EntityHelper.getInteger(rs, "durationMinutes");
        return new StudySessionEntity(
                EntityHelper.getLong(rs, "id"),
                EntityHelper.getLong(rs, "userId"),
                EntityHelper.getLong(rs, "topicId"),
                EntityHelper.getInstant(rs, "date"),
                duration != null ? duration : 0,
                EntityHelper.getString(rs, "notes")
        );
    }
}
