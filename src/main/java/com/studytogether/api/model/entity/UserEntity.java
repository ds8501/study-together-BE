package com.studytogether.api.model.entity;

import com.studytogether.api.model.dto.response.SafeUserResponse;
import com.studytogether.api.util.EntityHelper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import org.springframework.jdbc.core.RowMapper;

public record UserEntity(
        Long id,
        String name,
        String email,
        String passwordHash,
        String avatar,
        String timeZone,
        Instant createdAt) {

    public static final RowMapper<UserEntity> ROW_MAPPER = (rs, rowNum) -> fromResultSet(rs);

    public static UserEntity fromResultSet(ResultSet rs) throws SQLException {
        String tz = EntityHelper.getString(rs, "timeZone");
        return new UserEntity(
                EntityHelper.getLong(rs, "id"),
                EntityHelper.getString(rs, "name"),
                EntityHelper.getString(rs, "email"),
                EntityHelper.getString(rs, "passwordHash"),
                EntityHelper.getString(rs, "avatar"),
                tz != null ? tz : "Asia/Kolkata",
                EntityHelper.getInstant(rs, "createdAt")
        );
    }

    public SafeUserResponse toSafeUser() {
        return new SafeUserResponse(id, name, email, avatar);
    }
}
