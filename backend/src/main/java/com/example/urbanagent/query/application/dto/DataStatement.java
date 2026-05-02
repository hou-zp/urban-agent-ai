package com.example.urbanagent.query.application.dto;

import java.time.Instant;

public record DataStatement(
        String queryId,
        String metricCode,
        String metricName,
        String sourceSummary,
        String scopeSummary,
        Instant dataUpdatedAt,
        String permissionRewrite,
        String caliberVersion,
        String limitation
) {
}
