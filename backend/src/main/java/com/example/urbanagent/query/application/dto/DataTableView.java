package com.example.urbanagent.query.application.dto;

import java.util.List;

public record DataTableView(
        String tableName,
        String businessName,
        String permissionTag,
        String regionCode,
        List<DataFieldView> fields
) {
}
