package com.example.urbanagent.query.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record QueryExecuteView(
        String queryId,
        String executedSql,
        String summary,
        int rowCount,
        Instant executedAt,
        List<Map<String, Object>> rows,
        DataStatement dataStatement
) {
}
