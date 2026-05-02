package com.example.urbanagent.query.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record QueryCardView(
        String queryId,
        String question,
        String metricCode,
        String metricName,
        String scopeSummary,
        String resultSummary,
        String permissionRewrite,
        DataFragment dataFragment,
        DataStatement dataStatement,
        List<String> warnings,
        int rowCount,
        Instant executedAt,
        List<Map<String, Object>> rows
) {
}
