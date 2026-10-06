package com.studytogether.api.model.entity;

import com.studytogether.api.util.EntityHelper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import org.springframework.jdbc.core.RowMapper;

public record WorkspaceEntity(
        Long id,
        String name,
        String inviteCode,
        Instant createdAt) {

    public static final RowMapper<WorkspaceEntity> ROW_MAPPER = (rs, rowNum) -> fromResultSet(rs);

    public static WorkspaceEntity fromResultSet(ResultSet rs) throws SQLException {
        return new WorkspaceEntity(
                EntityHelper.getLong(rs, "id"),
                EntityHelper.getString(rs, "name"),
                EntityHelper.getString(rs, "inviteCode"),
                EntityHelper.getInstant(rs, "createdAt")
        );
    }
}
