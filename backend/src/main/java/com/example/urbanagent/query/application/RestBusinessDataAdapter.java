package com.example.urbanagent.query.application;

import com.example.urbanagent.common.config.RuntimeControlProperties;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.query.config.RestBusinessDataAdapterProperties;
import com.example.urbanagent.query.config.RestBusinessDataAdapterProperties.RestAdapterConfig;
import com.example.urbanagent.query.domain.DataSource;
import com.example.urbanagent.query.domain.DataSourceType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
public class RestBusinessDataAdapter implements BusinessDataAdapter {

    private static final TypeReference<List<Map<String, Object>>> ROW_LIST_TYPE = new TypeReference<>() {
    };

    private final RestClient.Builder restClientBuilder;
    private final RestBusinessDataAdapterProperties properties;
    private final RuntimeControlProperties runtimeControlProperties;
    private final ObjectMapper objectMapper;

    public RestBusinessDataAdapter(RestClient.Builder restClientBuilder,
                                   RestBusinessDataAdapterProperties properties,
                                   RuntimeControlProperties runtimeControlProperties,
                                   ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.properties = properties;
        this.runtimeControlProperties = runtimeControlProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(DataSource dataSource) {
        return dataSource != null && dataSource.getType() == DataSourceType.REST;
    }

    @Override
    public BusinessDataAdapterCapability describe(DataSource dataSource) {
        RestAdapterConfig config = requireConfig(dataSource);
        return new BusinessDataAdapterCapability(
                dataSource.getConnectionRef(),
                dataSource.getType(),
                dataSource.isReadOnly(),
                false,
                resolveTimeoutSeconds(config, null),
                config.getEndpoint(),
                config.getAuthRef()
        );
    }

    @Override
    public List<Map<String, Object>> execute(BusinessDataQueryRequest request) {
        validateRequest(request);
        RestAdapterConfig config = requireConfig(request.dataSource());
        RestClient client = restClientBuilder
                .baseUrl(config.getEndpoint())
                .build();
        RestQueryPayload payload = new RestQueryPayload(
                request.dataSource().getConnectionRef(),
                request.sql(),
                request.parameters() == null ? List.of() : List.copyOf(request.parameters()),
                resolveTimeoutSeconds(config, request.timeoutSeconds()),
                request.userContext().userId(),
                request.userContext().role(),
                request.userContext().region()
        );
        try {
            JsonNode body = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            return extractRows(body);
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.SQL_EXECUTION_FAILED, "REST 业务数据源调用失败: " + ex.getMessage());
        }
    }

    private void validateRequest(BusinessDataQueryRequest request) {
        if (request == null || request.dataSource() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "业务数据源请求不能为空");
        }
        if (!supports(request.dataSource())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前数据源类型暂不支持 REST 查询适配");
        }
        if (!request.dataSource().isReadOnly()) {
            throw new BusinessException(ErrorCode.SQL_EXECUTION_FAILED, "当前 REST 数据源未声明只读，禁止执行业务查询");
        }
        if (request.sql() == null || request.sql().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "业务查询 SQL 不能为空");
        }
        if (request.userContext() == null) {
            throw new BusinessException(ErrorCode.USER_CONTEXT_INVALID, "业务查询缺少用户权限上下文");
        }
    }

    private RestAdapterConfig requireConfig(DataSource dataSource) {
        if (!supports(dataSource)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前数据源类型暂不支持 REST 查询适配");
        }
        RestAdapterConfig config = properties.find(dataSource.getConnectionRef());
        if (config == null || config.getEndpoint() == null || config.getEndpoint().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "REST 业务数据源未配置 endpoint");
        }
        return config;
    }

    private int resolveTimeoutSeconds(RestAdapterConfig config, Integer requestTimeoutSeconds) {
        if (requestTimeoutSeconds != null && requestTimeoutSeconds > 0) {
            return requestTimeoutSeconds;
        }
        if (config.getTimeoutSeconds() != null && config.getTimeoutSeconds() > 0) {
            return config.getTimeoutSeconds();
        }
        return runtimeControlProperties.getSqlQueryTimeoutSeconds();
    }

    private List<Map<String, Object>> extractRows(JsonNode body) {
        if (body == null || body.isNull()) {
            return List.of();
        }
        try {
            if (body.isArray()) {
                return objectMapper.convertValue(body, ROW_LIST_TYPE);
            }
            JsonNode dataNode = body.path("data");
            if (dataNode.isArray()) {
                return objectMapper.convertValue(dataNode, ROW_LIST_TYPE);
            }
            JsonNode rowsNode = body.path("rows");
            if (rowsNode.isArray()) {
                return objectMapper.convertValue(rowsNode, ROW_LIST_TYPE);
            }
            JsonNode nestedRowsNode = dataNode.path("rows");
            if (nestedRowsNode.isArray()) {
                return objectMapper.convertValue(nestedRowsNode, ROW_LIST_TYPE);
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.SQL_EXECUTION_FAILED, "REST 业务数据源返回结果无法解析");
        }
        return List.of();
    }

    private record RestQueryPayload(
            String connectionRef,
            String sql,
            List<Object> parameters,
            int timeoutSeconds,
            String userId,
            String role,
            String region
    ) {
    }
}
