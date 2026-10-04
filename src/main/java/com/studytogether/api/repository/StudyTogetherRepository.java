package com.studytogether.api.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StudyTogetherRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public StudyTogetherRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> queryForList(String sql, Map<String, ?> params) {
        return jdbc.queryForList(sql, params);
    }

    public List<Map<String, Object>> queryForList(String sql, MapSqlParameterSource params) {
        return jdbc.queryForList(sql, params);
    }

    public Map<String, Object> queryForMap(String sql, Map<String, ?> params) {
        return jdbc.queryForMap(sql, params);
    }

    public int update(String sql, Map<String, ?> params) {
        return jdbc.update(sql, params);
    }

    public int update(String sql, MapSqlParameterSource params) {
        return jdbc.update(sql, params);
    }

    public <T> T queryForObject(String sql, Map<String, ?> params, Class<T> type) {
        return jdbc.queryForObject(sql, params, type);
    }

    public <T> T queryForObject(String sql, MapSqlParameterSource params, Class<T> type) {
        return jdbc.queryForObject(sql, params, type);
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbc.getJdbcTemplate();
    }
}
