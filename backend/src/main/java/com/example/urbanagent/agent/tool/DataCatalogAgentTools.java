package com.example.urbanagent.agent.tool;

import com.example.urbanagent.query.application.DataCatalogApplicationService;
import com.example.urbanagent.query.application.dto.DataFieldView;
import com.example.urbanagent.query.application.dto.DataTableView;
import com.example.urbanagent.query.application.dto.MetricDefinitionView;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataCatalogAgentTools {

    private final DataCatalogApplicationService dataCatalogApplicationService;

    public DataCatalogAgentTools(DataCatalogApplicationService dataCatalogApplicationService) {
        this.dataCatalogApplicationService = dataCatalogApplicationService;
    }

    @Tool(name = "data_catalog_search", description = "查询已授权的数据目录、指标和字段定义")
    @org.springframework.ai.tool.annotation.Tool(name = "data_catalog_search", description = "查询已授权的数据目录、指标和字段定义")
    public String dataCatalogSearch(@ToolParam(name = "query", description = "指标名、表名或业务关键词")
                                    @org.springframework.ai.tool.annotation.ToolParam(description = "指标名、表名或业务关键词", required = true)
                                    String query) {
        List<MetricDefinitionView> metrics = dataCatalogApplicationService.listMetrics()
                .stream()
                .filter(metric -> contains(metric.metricCode(), query) || contains(metric.metricName(), query))
                .limit(3)
                .toList();
        List<DataTableView> tables = dataCatalogApplicationService.listAuthorizedTables(query)
                .stream()
                .limit(3)
                .toList();
        if (metrics.isEmpty() && tables.isEmpty()) {
            return "未找到已授权的数据目录。";
        }
        StringBuilder builder = new StringBuilder();
        if (!metrics.isEmpty()) {
            builder.append("指标：\n");
            for (MetricDefinitionView metric : metrics) {
                builder.append("- ")
                        .append(metric.metricName())
                        .append("（").append(metric.metricCode()).append("）")
                        .append("，口径：").append(metric.description())
                        .append("，默认时间字段：").append(metric.defaultTimeField())
                        .append('\n');
            }
        }
        if (!tables.isEmpty()) {
            builder.append("表与字段：\n");
            for (DataTableView table : tables) {
                builder.append("- ").append(table.businessName())
                        .append("（").append(table.tableName()).append("）")
                        .append("，字段：");
                String fields = table.fields().stream()
                        .map(DataFieldView::fieldName)
                        .limit(6)
                        .reduce((left, right) -> left + ", " + right)
                        .orElse("无");
                builder.append(fields).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private boolean contains(String value, String keyword) {
        return value != null && keyword != null && value.toLowerCase(java.util.Locale.ROOT).contains(keyword.trim().toLowerCase(java.util.Locale.ROOT));
    }
}
