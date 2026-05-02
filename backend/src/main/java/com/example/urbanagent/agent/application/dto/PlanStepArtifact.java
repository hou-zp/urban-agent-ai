package com.example.urbanagent.agent.application.dto;

import java.util.List;

public record PlanStepArtifact(
        String kind,
        String queryId,
        String validatedSql,
        String executedSql,
        String metricCode,
        String metricName,
        String permissionRewrite,
        Integer rowCount,
        List<String> warnings,
        List<String> documentIds,
        String summary
) {
}
