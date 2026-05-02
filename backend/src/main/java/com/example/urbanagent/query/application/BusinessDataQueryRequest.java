package com.example.urbanagent.query.application;

import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.query.domain.DataSource;

import java.util.List;

public record BusinessDataQueryRequest(
        DataSource dataSource,
        String sql,
        List<Object> parameters,
        Integer timeoutSeconds,
        UserContext userContext
) {
}
