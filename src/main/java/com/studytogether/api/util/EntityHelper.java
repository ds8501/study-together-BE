package com.studytogether.api.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

public final class EntityHelper {
    private EntityHelper() {}

    public static boolean hasColumn(ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public static Long getLong(ResultSet rs, String column) throws SQLException {
        if (!hasColumn(rs, column)) return null;
        long val = rs.getLong(column);
        return rs.wasNull() ? null : val;
    }

    public static Integer getInteger(ResultSet rs, String column) throws SQLException {
        if (!hasColumn(rs, column)) return null;
        int val = rs.getInt(column);
        return rs.wasNull() ? null : val;
    }

    public static Double getDouble(ResultSet rs, String column) throws SQLException {
        if (!hasColumn(rs, column)) return null;
        double val = rs.getDouble(column);
        return rs.wasNull() ? null : val;
    }

    public static String getString(ResultSet rs, String column) throws SQLException {
        if (!hasColumn(rs, column)) return null;
        return rs.getString(column);
    }

    public static Instant getInstant(ResultSet rs, String column) throws SQLException {
        if (!hasColumn(rs, column)) return null;
        java.sql.Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : null;
    }
}
