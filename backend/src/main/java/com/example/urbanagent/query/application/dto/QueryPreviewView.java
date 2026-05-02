package com.example.urbanagent.query.application.dto;

import java.util.List;

public record QueryPreviewView(
        String queryId,
        String metricCode,
        String metricName,
        String candidateSql,
        String validatedSql,
        String permissionRewrite,
        String summary,
        List<String> warnings,
        DataStatement dataStatement
) {
}
