package com.example.urbanagent.query.application;

import com.example.urbanagent.common.config.RuntimeControlProperties;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.query.domain.DataSource;
import com.example.urbanagent.query.domain.DataSourceType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class JdbcBusinessDataAdapter implements BusinessDataAdapter {

    private static final Set<DataSourceType> SUPPORTED_TYPES = Set.of(
            DataSourceType.POSTGRESQL,
            DataSourceType.MYSQL,
            DataSourceType.VIEW
    );

    private final JdbcTemplate jdbcTemplate;
    private final RuntimeControlProperties runtimeControlProperties;

    public JdbcBusinessDataAdapter(JdbcTemplate jdbcTemplate,
                                   RuntimeControlProperties runtimeControlProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.runtimeControlProperties = runtimeControlProperties;
    }

    @Override
    public boolean supports(DataSource dataSource) {
        return dataSource != null && SUPPORTED_TYPES.contains(dataSource.getType());
    }

    @Override
    public BusinessDataAdapterCapability describe(DataSource dataSource) {
        if (!supports(dataSource)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前数据源类型暂不支持 JDBC 查询适配");
        }
        return new BusinessDataAdapterCapability(
                dataSource.getConnectionRef(),
                dataSource.getType(),
                dataSource.isReadOnly(),
                true,
                runtimeControlProperties.getSqlQueryTimeoutSeconds(),
                null,
                null
        );
    }

    @Override
    public List<Map<String, Object>> execute(BusinessDataQueryRequest request) {
        validateRequest(request);
        try {
            ResultSetExtractor<List<Map<String, Object>>> extractor = this::mapRows;
            return jdbcTemplate.query(connection -> {
                PreparedStatement statement = connection.prepareStatement(request.sql());
                List<Object> parameters = normalizeParameters(request.parameters());
                for (int index = 0; index < parameters.size(); index++) {
                    statement.setObject(index + 1, parameters.get(index));
                }
                statement.setQueryTimeout(resolveTimeoutSeconds(request));
                return statement;
            }, extractor);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SQL_EXECUTION_FAILED, ex.getMessage());
        }
    }

    private void validateRequest(BusinessDataQueryRequest request) {
        if (request == null || request.dataSource() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "业务数据源请求不能为空");
        }
        if (!supports(request.dataSource())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前数据源类型暂不支持 JDBC 查询适配");
        }
        if (!request.dataSource().isReadOnly()) {
            throw new BusinessException(ErrorCode.SQL_EXECUTION_FAILED, "当前数据源未声明只读，禁止执行业务查询");
        }
        if (request.sql() == null || request.sql().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "业务查询 SQL 不能为空");
        }
        if (request.userContext() == null) {
            throw new BusinessException(ErrorCode.USER_CONTEXT_INVALID, "业务查询缺少用户权限上下文");
        }
    }

    private int resolveTimeoutSeconds(BusinessDataQueryRequest request) {
        Integer timeoutSeconds = request.timeoutSeconds();
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            return runtimeControlProperties.getSqlQueryTimeoutSeconds();
        }
        return timeoutSeconds;
    }

    private List<Object> normalizeParameters(List<Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(parameters);
    }

    private List<Map<String, Object>> mapRows(ResultSet resultSet) throws java.sql.SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();
        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int index = 1; index <= columnCount; index++) {
                row.put(metadata.getColumnLabel(index), resultSet.getObject(index));
            }
            rows.add(row);
        }
        return rows;
    }
}
