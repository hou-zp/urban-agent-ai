package com.example.urbanagent.query.application.dto;

import java.util.List;

public record DataFragment(
        String queryId,
        List<String> fields,
        int rowCount,
        String summary
) {
}
