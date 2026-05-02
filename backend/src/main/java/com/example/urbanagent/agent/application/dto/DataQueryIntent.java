package com.example.urbanagent.agent.application.dto;

import java.util.List;

public record DataQueryIntent(
        String metricCode,
        AnalysisIntent analysisIntent,
        List<String> dimensions,
        String timeExpression,
        String regionCode
) {

    public DataQueryIntent {
        metricCode = normalize(metricCode);
        analysisIntent = analysisIntent == null ? AnalysisIntent.TOTAL : analysisIntent;
        dimensions = dimensions == null ? List.of() : dimensions.stream()
                .map(DataQueryIntent::normalize)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        timeExpression = normalize(timeExpression);
        regionCode = normalize(regionCode);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
