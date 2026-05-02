package com.example.urbanagent.query.application.dto;

public record BusinessRecordFieldView(
        String fieldName,
        String businessName,
        String dataType,
        String sensitiveLevel,
        boolean masked
) {
}
