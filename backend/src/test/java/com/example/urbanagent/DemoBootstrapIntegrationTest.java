package com.example.urbanagent;

import com.example.urbanagent.knowledge.repository.KnowledgeDocumentRepository;
import com.example.urbanagent.query.repository.MetricDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:urban-agent-bootstrap-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "urban-agent.bootstrap.demo-data.enabled=true",
        "urban-agent.bootstrap.demo-data.reset-on-startup=true"
})
class DemoBootstrapIntegrationTest {

    @Autowired
    private MetricDefinitionRepository metricDefinitionRepository;

    @Autowired
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Test
    void shouldBootstrapDemoDataOnStartup() {
        assertThat(metricDefinitionRepository.count()).isEqualTo(9);
        assertThat(knowledgeDocumentRepository.count()).isEqualTo(3);
        assertThat(knowledgeDocumentRepository.existsByDocumentNumber("城管规〔2026〕8号")).isTrue();
    }
}
