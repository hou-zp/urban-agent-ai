package com.example.urbanagent;

import com.example.urbanagent.common.config.RuntimeControlProperties;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.query.application.BusinessDataAdapterCapability;
import com.example.urbanagent.query.application.BusinessDataQueryRequest;
import com.example.urbanagent.query.application.RestBusinessDataAdapter;
import com.example.urbanagent.query.config.RestBusinessDataAdapterProperties;
import com.example.urbanagent.query.config.RestBusinessDataAdapterProperties.RestAdapterConfig;
import com.example.urbanagent.query.domain.DataSource;
import com.example.urbanagent.query.domain.DataSourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestBusinessDataAdapterTest {

    @Test
    void shouldDescribeConfiguredRestAdapter() {
        RestBusinessDataAdapter adapter = new RestBusinessDataAdapter(
                RestClient.builder(),
                configuredProperties(),
                runtimeProperties(),
                new ObjectMapper()
        );
        DataSource source = new DataSource("REST 业务源", DataSourceType.REST, "oil-fume-rest", true);

        BusinessDataAdapterCapability capability = adapter.describe(source);

        assertThat(capability.connectionRef()).isEqualTo("oil-fume-rest");
        assertThat(capability.endpoint()).isEqualTo("http://localhost:8088/mock/query");
        assertThat(capability.authRef()).isEqualTo("token:oil-fume");
        assertThat(capability.timeoutSeconds()).isEqualTo(18);
        assertThat(capability.supportsSqlQuery()).isFalse();
    }

    @Test
    void shouldPostRestPayloadAndParseRows() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8088/mock/query"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "connectionRef":"oil-fume-rest",
                          "sql":"select unit_name, warning_count from remote_warning",
                          "parameters":["高"],
                          "timeoutSeconds":9,
                          "userId":"demo-user",
                          "role":"MANAGER",
                          "region":"shaoxing-keqiao"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "rows": [
                              {
                                "unit_name": "兰亭饭店",
                                "warning_count": 3
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        RestBusinessDataAdapter adapter = new RestBusinessDataAdapter(
                builder,
                configuredProperties(),
                runtimeProperties(),
                new ObjectMapper()
        );

        List<Map<String, Object>> rows = adapter.execute(new BusinessDataQueryRequest(
                new DataSource("REST 业务源", DataSourceType.REST, "oil-fume-rest", true),
                "select unit_name, warning_count from remote_warning",
                List.of("高"),
                9,
                new UserContext("demo-user", "MANAGER", "shaoxing-keqiao")
        ));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("unit_name", "兰亭饭店");
        assertThat(rows.get(0)).containsEntry("warning_count", 3);
        server.verify();
    }

    @Test
    void shouldRejectMissingEndpointConfig() {
        RestBusinessDataAdapterProperties properties = new RestBusinessDataAdapterProperties();
        RestBusinessDataAdapter adapter = new RestBusinessDataAdapter(
                RestClient.builder(),
                properties,
                runtimeProperties(),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> adapter.describe(new DataSource("REST 业务源", DataSourceType.REST, "missing", true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("endpoint");
    }

    private RestBusinessDataAdapterProperties configuredProperties() {
        RestBusinessDataAdapterProperties properties = new RestBusinessDataAdapterProperties();
        RestAdapterConfig config = new RestAdapterConfig();
        config.setEndpoint("http://localhost:8088/mock/query");
        config.setAuthRef("token:oil-fume");
        config.setTimeoutSeconds(18);
        Map<String, RestAdapterConfig> configs = new LinkedHashMap<>();
        configs.put("oil-fume-rest", config);
        properties.setConfigs(configs);
        return properties;
    }

    private RuntimeControlProperties runtimeProperties() {
        RuntimeControlProperties properties = new RuntimeControlProperties();
        properties.setSqlQueryTimeoutSeconds(30);
        return properties;
    }
}
