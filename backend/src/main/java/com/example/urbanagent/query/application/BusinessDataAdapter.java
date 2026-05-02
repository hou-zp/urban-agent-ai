package com.example.urbanagent.query.application;

import com.example.urbanagent.query.domain.DataSource;

import java.util.List;
import java.util.Map;

public interface BusinessDataAdapter {

    boolean supports(DataSource dataSource);

    BusinessDataAdapterCapability describe(DataSource dataSource);

    List<Map<String, Object>> execute(BusinessDataQueryRequest request);
}
