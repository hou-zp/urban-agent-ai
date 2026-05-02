package com.example.urbanagent.query.application.dto;

public record DataFieldView(
        String fieldName,
        String businessName,
        String dataType,
        String sensitiveLevel
) {
}
