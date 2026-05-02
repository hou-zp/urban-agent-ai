package com.example.urbanagent.query.application;

import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.example.urbanagent.query.domain.DataSource;
import com.example.urbanagent.query.domain.DataSourceType;
import com.example.urbanagent.query.repository.DataSourceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ReadonlySqlQueryService {

    private static final DataSource DEFAULT_READONLY_SOURCE = new DataSource(
            "默认只读业务库",
            DataSourceType.POSTGRESQL,
            "primary-jdbc",
            true
    );

    private final JdbcBusinessDataAdapter jdbcBusinessDataAdapter;
    private final DataSourceRepository dataSourceRepository;

    public ReadonlySqlQueryService(JdbcBusinessDataAdapter jdbcBusinessDataAdapter,
                                   DataSourceRepository dataSourceRepository) {
        this.jdbcBusinessDataAdapter = jdbcBusinessDataAdapter;
        this.dataSourceRepository = dataSourceRepository;
    }

    public List<Map<String, Object>> execute(String sql) {
        try {
            return jdbcBusinessDataAdapter.execute(new BusinessDataQueryRequest(
                    resolveReadonlyDataSource(),
                    sql,
                    List.of(),
                    null,
                    UserContextHolder.get()
            ));
        } catch (Exception ex) {
            if (ex instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.SQL_EXECUTION_FAILED, ex.getMessage());
        }
    }

    private DataSource resolveReadonlyDataSource() {
        return dataSourceRepository.findAll().stream()
                .filter(DataSource::isReadOnly)
                .findFirst()
                .orElse(DEFAULT_READONLY_SOURCE);
    }
}
