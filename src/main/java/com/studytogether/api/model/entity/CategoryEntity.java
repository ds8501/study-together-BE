package com.studytogether.api.model.entity;

import com.studytogether.api.util.EntityHelper;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

public record CategoryEntity(
        Long id,
        Long studyPlanId,
        String name,
        String description,
        String icon,
        String color,
        int order) {

    public static final RowMapper<CategoryEntity> ROW_MAPPER = (rs, rowNum) -> fromResultSet(rs);

    public static CategoryEntity fromResultSet(ResultSet rs) throws SQLException {
        Integer order = EntityHelper.getInteger(rs, "order");
        return new CategoryEntity(
                EntityHelper.getLong(rs, "id"),
                EntityHelper.getLong(rs, "studyPlanId"),
                EntityHelper.getString(rs, "name"),
                EntityHelper.getString(rs, "description"),
                EntityHelper.getString(rs, "icon"),
                EntityHelper.getString(rs, "color"),
                order != null ? order : 0
        );
    }
}
