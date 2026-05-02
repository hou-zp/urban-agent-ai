package com.example.urbanagent.query.application.dto;

import java.util.List;
import java.util.Map;

public record BusinessRecordQueryView(
        String recordQueryId,
        BusinessRecordType recordType,
        String tableName,
        String businessName,
        String permissionTag,
        String regionCode,
        List<String> maskedFields,
        List<BusinessRecordFieldView> fields,
        List<Map<String, Object>> rows
) {
}
