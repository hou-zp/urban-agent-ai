package com.example.urbanagent.query.application.dto;

public record DataCatalogSyncResult(
        int dataSourceCount,
        int tableCount,
        int fieldCount,
        int metricCount
) {
}
