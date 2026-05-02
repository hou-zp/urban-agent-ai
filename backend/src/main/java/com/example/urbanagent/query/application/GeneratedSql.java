package com.example.urbanagent.query.application;

import java.util.List;

public record GeneratedSql(
        String metricCode,
        String metricName,
        String candidateSql,
        String summary,
        List<String> warnings
) {
}
