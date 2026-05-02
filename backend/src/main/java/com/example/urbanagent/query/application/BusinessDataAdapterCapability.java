package com.example.urbanagent.query.application;

import com.example.urbanagent.query.domain.DataSourceType;

public record BusinessDataAdapterCapability(
        String connectionRef,
        DataSourceType dataSourceType,
        boolean readOnly,
        boolean supportsSqlQuery,
        int timeoutSeconds,
        String endpoint,
        String authRef
) {
}
