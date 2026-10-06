package com.studytogether.api.model.entity;

import com.studytogether.api.util.EntityHelper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import org.springframework.jdbc.core.RowMapper;

public record StudyStreakEntity(
        Long userId,
        int currentStreak,
        int bestStreak,
        Instant lastStudyDate,
        Instant updatedAt) {

    public static final RowMapper<StudyStreakEntity> ROW_MAPPER = (rs, rowNum) -> fromResultSet(rs);

    public static StudyStreakEntity fromResultSet(ResultSet rs) throws SQLException {
        Integer current = EntityHelper.getInteger(rs, "currentStreak");
        Integer best = EntityHelper.getInteger(rs, "bestStreak");
        return new StudyStreakEntity(
                EntityHelper.getLong(rs, "userId"),
                current != null ? current : 0,
                best != null ? best : 0,
                EntityHelper.getInstant(rs, "lastStudyDate"),
                EntityHelper.getInstant(rs, "updatedAt")
        );
    }
}
