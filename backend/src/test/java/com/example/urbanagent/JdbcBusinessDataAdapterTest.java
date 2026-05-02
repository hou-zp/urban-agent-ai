package com.example.urbanagent;

import com.example.urbanagent.common.config.RuntimeControlProperties;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.query.application.BusinessDataAdapterCapability;
import com.example.urbanagent.query.application.BusinessDataQueryRequest;
import com.example.urbanagent.query.application.JdbcBusinessDataAdapter;
import com.example.urbanagent.query.domain.DataSource;
import com.example.urbanagent.query.domain.DataSourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcBusinessDataAdapterTest {

    private JdbcBusinessDataAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:jdbc-business-data-adapter-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("drop table if exists sample_metric");
        jdbcTemplate.execute("create table sample_metric (street_name varchar(64), metric_value int)");
        jdbcTemplate.update("insert into sample_metric(street_name, metric_value) values (?, ?)", "兰亭街道", 12);
        jdbcTemplate.update("insert into sample_metric(street_name, metric_value) values (?, ?)", "柯岩街道", 8);

        RuntimeControlProperties runtimeControlProperties = new RuntimeControlProperties();
        runtimeControlProperties.setSqlQueryTimeoutSeconds(12);
        adapter = new JdbcBusinessDataAdapter(jdbcTemplate, runtimeControlProperties);
    }

    @Test
    void shouldDescribeJdbcReadonlyCapability() {
        DataSource source = new DataSource("城管业务库", DataSourceType.POSTGRESQL, "urban-ops-readonly", true);

        BusinessDataAdapterCapability capability = adapter.describe(source);

        assertThat(capability.connectionRef()).isEqualTo("urban-ops-readonly");
        assertThat(capability.dataSourceType()).isEqualTo(DataSourceType.POSTGRESQL);
        assertThat(capability.readOnly()).isTrue();
        assertThat(capability.supportsSqlQuery()).isTrue();
        assertThat(capability.timeoutSeconds()).isEqualTo(12);
    }

    @Test
    void shouldExecuteReadonlySqlWithPermissionContext() {
        DataSource source = new DataSource("城管业务库", DataSourceType.POSTGRESQL, "urban-ops-readonly", true);

        List<Map<String, Object>> rows = adapter.execute(new BusinessDataQueryRequest(
                source,
                "select street_name, metric_value from sample_metric where metric_value >= ? order by metric_value desc",
                List.of(10),
                5,
                new UserContext("demo-user", "OFFICER", "district-a")
        ));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("STREET_NAME", "兰亭街道");
        assertThat(rows.get(0)).containsEntry("METRIC_VALUE", 12);
    }

    @Test
    void shouldRejectNonReadonlySource() {
        DataSource source = new DataSource("写库", DataSourceType.POSTGRESQL, "urban-ops-write", false);

        assertThatThrownBy(() -> adapter.execute(new BusinessDataQueryRequest(
                source,
                "select street_name from sample_metric",
                List.of(),
                null,
                new UserContext("demo-user", "OFFICER", "district-a")
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.SQL_EXECUTION_FAILED);
    }
}
