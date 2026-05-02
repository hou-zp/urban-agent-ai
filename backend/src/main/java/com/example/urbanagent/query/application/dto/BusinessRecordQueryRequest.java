package com.example.urbanagent.query.application.dto;

public record BusinessRecordQueryRequest(
        BusinessRecordType recordType,
        String keyword,
        String regionCode,
        String streetName,
        String status,
        String timeRange,
        Integer limit
) {
}
