package com.example.urbanagent.bootstrap.application;

import com.example.urbanagent.common.config.DemoBootstrapProperties;
import com.example.urbanagent.knowledge.application.KnowledgeApplicationService;
import com.example.urbanagent.knowledge.repository.KnowledgeDocumentRepository;
import com.example.urbanagent.query.application.DataCatalogApplicationService;
import com.example.urbanagent.query.repository.MetricDefinitionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

@Component
public class DemoDataBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataBootstrapService.class);
    private static final long EXPECTED_DEMO_METRIC_COUNT = 9L;

    private final DemoBootstrapProperties properties;
    private final DataCatalogApplicationService dataCatalogApplicationService;
    private final MetricDefinitionRepository metricDefinitionRepository;
    private final KnowledgeApplicationService knowledgeApplicationService;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public DemoDataBootstrapService(DemoBootstrapProperties properties,
                                    DataCatalogApplicationService dataCatalogApplicationService,
                                    MetricDefinitionRepository metricDefinitionRepository,
                                    KnowledgeApplicationService knowledgeApplicationService,
                                    KnowledgeDocumentRepository knowledgeDocumentRepository,
                                    JdbcTemplate jdbcTemplate,
                                    DataSource dataSource) {
        this.properties = properties;
        this.dataCatalogApplicationService = dataCatalogApplicationService;
        this.metricDefinitionRepository = metricDefinitionRepository;
        this.knowledgeApplicationService = knowledgeApplicationService;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean demoBootstrapEnabled = properties.isEnabled();
        boolean localH2CatalogBootstrap = isH2DataSource();

        if (!demoBootstrapEnabled && !localH2CatalogBootstrap) {
            return;
        }

        bootstrapCatalog();
        if (demoBootstrapEnabled) {
            bootstrapKnowledge();
        }
    }

    private void bootstrapCatalog() {
        if (properties.isResetOnStartup()
                || metricDefinitionRepository.count() < EXPECTED_DEMO_METRIC_COUNT
                || !hasOilFumeThresholdConfig()) {
            dataCatalogApplicationService.syncDemoCatalog();
            log.info("demo catalog bootstrap completed");
        }
    }

    private boolean hasOilFumeThresholdConfig() {
        try {
            Integer count = jdbcTemplate.queryForObject("select count(*) from oil_fume_threshold_config", Integer.class);
            return count != null && count > 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean isH2DataSource() {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            return url != null && url.startsWith("jdbc:h2:");
        } catch (SQLException ex) {
            log.warn("failed to inspect datasource url for demo catalog bootstrap", ex);
            return false;
        }
    }

    private void bootstrapKnowledge() {
        if (properties.isResetOnStartup()) {
            knowledgeApplicationService.resetAndSeedDemoDocuments();
            log.info("demo knowledge bootstrap completed with reset");
            return;
        }
        if (knowledgeDocumentRepository.count() == 0) {
            knowledgeApplicationService.seedDemoDocuments();
            log.info("demo knowledge bootstrap completed");
        }
    }
}
