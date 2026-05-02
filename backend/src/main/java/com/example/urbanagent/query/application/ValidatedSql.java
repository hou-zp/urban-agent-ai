package com.example.urbanagent.query.application;

public record ValidatedSql(
        String tableName,
        String sql
) {
}
