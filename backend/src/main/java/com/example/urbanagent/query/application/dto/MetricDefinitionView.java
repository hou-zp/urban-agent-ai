package com.example.urbanagent.query.application.dto;

import java.time.Instant;

public record MetricDefinitionView(
        String metricCode,
        String metricName,
        String description,
        String aggregationExpr,
        String defaultTimeField,
        String commonDimensions,
        String tableName,
        String caliberVersion,
        String dataQuality,
        String applicableRegion,
        Instant dataUpdatedAt
) {
}
