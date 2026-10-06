package com.studytogether.api.model.entity;

import com.studytogether.api.util.EntityHelper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import org.springframework.jdbc.core.RowMapper;

public record WorkspaceMemberEntity(
        Long id,
        Long workspaceId,
        Long userId,
        MemberRole role,
        Instant joinedAt) {

    public enum MemberRole { OWNER, MEMBER }

    public static final RowMapper<WorkspaceMemberEntity> ROW_MAPPER = (rs, rowNum) -> fromResultSet(rs);

    public static WorkspaceMemberEntity fromResultSet(ResultSet rs) throws SQLException {
        String roleStr = EntityHelper.getString(rs, "role");
        MemberRole role = MemberRole.MEMBER;
        if (roleStr != null) {
            try {
                role = MemberRole.valueOf(roleStr);
            } catch (IllegalArgumentException ignored) {}
        }
        return new WorkspaceMemberEntity(
                EntityHelper.getLong(rs, "id"),
                EntityHelper.getLong(rs, "workspaceId"),
                EntityHelper.getLong(rs, "userId"),
                role,
                EntityHelper.getInstant(rs, "joinedAt")
        );
    }
}
