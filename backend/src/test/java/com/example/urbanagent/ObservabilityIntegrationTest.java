package com.example.urbanagent;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:urban-agent-observability-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class ObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private ObservationRegistry observationRegistry;

    @Autowired
    private ObjectProvider<Tracer> tracerProvider;

    @Test
    void shouldExposeActuatorHealthEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldKeepActuatorSurfaceLimited() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void shouldConfigureJsonLoggingWithMdcFields() throws Exception {
        Resource resource = resourceLoader.getResource("classpath:logback-spring.xml");
        String xml = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(xml).contains("LoggingEventCompositeJsonEncoder");
        assertThat(xml).contains("<mdc/>");
        assertThat(xml).contains("\"application\"");
        assertThat(xml).contains("<fieldName>stacktrace</fieldName>");
        assertThat(xml).contains("SensitiveLogMaskingJsonGeneratorDecorator");
    }

    @Test
    void shouldProvideObservationAndTracingInfrastructure() {
        assertThat(observationRegistry).isNotNull();
        assertThat(tracerProvider.getIfAvailable()).isNotNull();
    }
}
