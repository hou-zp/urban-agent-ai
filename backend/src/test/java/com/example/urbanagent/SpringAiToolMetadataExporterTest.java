package com.example.urbanagent;

import com.example.urbanagent.agent.tool.BusinessRecordAgentTools;
import com.example.urbanagent.agent.tool.DataCatalogAgentTools;
import com.example.urbanagent.agent.tool.KnowledgeAgentTools;
import com.example.urbanagent.agent.tool.QueryAgentTools;
import com.example.urbanagent.agent.tool.RiskAgentTools;
import com.example.urbanagent.agent.tool.SpringAiToolMetadataExporter;
import com.example.urbanagent.agent.tool.ToolDefinition;
import com.example.urbanagent.agent.tool.ToolRiskLevel;
import com.example.urbanagent.agent.tool.UrbanToolRegistry;
import com.example.urbanagent.knowledge.application.KnowledgeSearchService;
import com.example.urbanagent.query.application.BusinessRecordQueryService;
import com.example.urbanagent.query.application.DataCatalogApplicationService;
import com.example.urbanagent.query.application.Nl2SqlService;
import com.example.urbanagent.query.application.OrganizationDimensionTranslator;
import com.example.urbanagent.query.application.ReadonlySqlQueryService;
import com.example.urbanagent.query.application.SqlPermissionService;
import com.example.urbanagent.query.application.SqlValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.urbanagent.risk.application.RiskWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SpringAiToolMetadataExporterTest {

    @Test
    void shouldExportSpringAiAlignedToolDefinitions() {
        UrbanToolRegistry urbanToolRegistry = new UrbanToolRegistry(
                new KnowledgeAgentTools(mock(KnowledgeSearchService.class)),
                new DataCatalogAgentTools(mock(DataCatalogApplicationService.class)),
                new QueryAgentTools(
                        mock(Nl2SqlService.class),
                        mock(SqlValidationService.class),
                        mock(SqlPermissionService.class),
                        mock(ReadonlySqlQueryService.class),
                        mock(OrganizationDimensionTranslator.class)
                ),
                new BusinessRecordAgentTools(mock(BusinessRecordQueryService.class), new ObjectMapper()),
                new RiskAgentTools(mock(RiskWorkflowService.class))
        );
        SpringAiToolMetadataExporter exporter = new SpringAiToolMetadataExporter(urbanToolRegistry);

        List<ToolDefinition> definitions = exporter.export();

        assertThat(definitions).hasSize(9);
        assertThat(definitions)
                .extracting(ToolDefinition::name)
                .contains("knowledge_search", "readonly_sql_query", "business_record_query", "user_confirm");
        ToolDefinition readonlySqlQuery = definitions.stream()
                .filter(item -> item.name().equals("readonly_sql_query"))
                .findFirst()
                .orElseThrow();
        assertThat(readonlySqlQuery.description()).contains("只读 SQL 查询");
        assertThat(readonlySqlQuery.riskLevel()).isEqualTo(ToolRiskLevel.HIGH);
        assertThat(readonlySqlQuery.permissionTags()).contains("query:execute");
        assertThat(readonlySqlQuery.parameters())
                .singleElement()
                .satisfies(parameter -> {
                    assertThat(parameter.name()).isEqualTo("sql");
                    assertThat(parameter.description()).contains("通过校验的 SQL");
                    assertThat(parameter.required()).isTrue();
                });

        ToolDefinition userConfirm = definitions.stream()
                .filter(item -> item.name().equals("user_confirm"))
                .findFirst()
                .orElseThrow();
        assertThat(userConfirm.parameters())
                .extracting(parameter -> parameter.name())
                .containsExactly("action", "reason");
    }

    @Test
    void shouldCreateSpringAiToolCallbackProvider() {
        UrbanToolRegistry urbanToolRegistry = new UrbanToolRegistry(
                new KnowledgeAgentTools(mock(KnowledgeSearchService.class)),
                new DataCatalogAgentTools(mock(DataCatalogApplicationService.class)),
                new QueryAgentTools(
                        mock(Nl2SqlService.class),
                        mock(SqlValidationService.class),
                        mock(SqlPermissionService.class),
                        mock(ReadonlySqlQueryService.class),
                        mock(OrganizationDimensionTranslator.class)
                ),
                new BusinessRecordAgentTools(mock(BusinessRecordQueryService.class), new ObjectMapper()),
                new RiskAgentTools(mock(RiskWorkflowService.class))
        );
        SpringAiToolMetadataExporter exporter = new SpringAiToolMetadataExporter(urbanToolRegistry);

        ToolCallbackProvider toolCallbackProvider = exporter.createToolCallbackProvider();

        assertThat(toolCallbackProvider).isNotNull();
        assertThat(toolCallbackProvider.getToolCallbacks()).hasSize(9);
    }
}
