package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.ChartSpec;
import com.example.urbanagent.agent.application.dto.ChartSpec.ChartType;
import com.example.urbanagent.query.application.dto.QueryCardView;
import com.example.urbanagent.query.application.dto.QueryExecuteView;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 图表生成服务。
 *
 * <p>将业务查询结果转换为可视化图表规格。
 * 图表数据必须来自真实查询结果，绑定 queryId，不允许模型凭空生成。
 *
 * <p>生成规则：
 * <ul>
 *   <li>单行聚合结果 → 指标卡（无图表）</li>
 *   <li>时间维度分布（>= 3 个时间点）→ 折线图</li>
 *   <li>类别维度分布（>= 2 个类别）→ 柱状图</li>
 *   <li>占比分析（类别 <= 5）→ 饼图</li>
 *   <li>多维度复杂字段 → 表格</li>
 * </ul>
 */
@Service
public class ChartService {

    private final ChartProperties chartProperties;

    public ChartService(ChartProperties chartProperties) {
        this.chartProperties = chartProperties;
    }

    /**
     * 从查询卡片构建图表规格。
     *
     * @param card 查询卡片
     * @return 图表规格列表（可能为空）
     */
    public List<ChartSpec> buildFromCard(QueryCardView card) {
        if (card == null || card.rows() == null || card.rows().isEmpty()) {
            return List.of();
        }
        if (card.queryId() == null || card.queryId().isBlank()) {
            return List.of();
        }

        List<Map<String, Object>> rows = card.rows();
        List<String> fields = rows.get(0).keySet().stream().toList();
        ChartType chartType = inferChartType(fields, rows);
        if (chartType == null) {
            return List.of();
        }

        String title = buildTitle(card.metricName(), chartType);
        List<String> xFields = resolveXFields(fields, chartType);
        List<String> yFields = resolveYFields(fields, chartType);
        String dataSourceName = card.dataStatement() != null ? card.dataStatement().sourceSummary() : "";
        String caliber = card.dataStatement() != null ? card.dataStatement().scopeSummary() : "";

        return switch (chartType) {
            case LINE -> List.of(ChartSpec.line(
                    "chart-" + UUID.randomUUID().toString().substring(0, 8),
                    title,
                    card.queryId(),
                    dataSourceName,
                    caliber,
                    xFields,
                    yFields,
                    rows
            ));
            case PIE -> List.of(ChartSpec.pie(
                    "chart-" + UUID.randomUUID().toString().substring(0, 8),
                    title,
                    card.queryId(),
                    dataSourceName,
                    caliber,
                    xFields,
                    yFields,
                    rows
            ));
            case TABLE -> List.of(ChartSpec.table(
                    "chart-" + UUID.randomUUID().toString().substring(0, 8),
                    title,
                    card.queryId(),
                    dataSourceName,
                    caliber,
                    fields,
                    rows
            ));
            default -> List.of(ChartSpec.bar(
                    "chart-" + UUID.randomUUID().toString().substring(0, 8),
                    title,
                    card.queryId(),
                    dataSourceName,
                    caliber,
                    xFields,
                    yFields,
                    rows
            ));
        };
    }

    /**
     * 从查询执行结果构建图表规格。
     *
     * @param result 查询执行结果
     * @return 图表规格列表（可能为空）
     */
    public List<ChartSpec> buildFromResult(QueryExecuteView result) {
        if (result == null || result.rows() == null || result.rows().isEmpty()) {
            return List.of();
        }
        if (result.queryId() == null || result.queryId().isBlank()) {
            return List.of();
        }

        List<Map<String, Object>> rows = result.rows();
        List<String> fields = rows.get(0).keySet().stream().toList();
        ChartType chartType = inferChartType(fields, rows);
        if (chartType == null) {
            return List.of();
        }

        String dataSourceName = result.dataStatement() != null ? result.dataStatement().sourceSummary() : "数据查询";
        String caliber = result.dataStatement() != null ? result.dataStatement().scopeSummary() : "";

        return switch (chartType) {
            case BAR -> List.of(ChartSpec.bar(
                    "chart-" + UUID.randomUUID().toString().substring(0, 8),
                    "数据查询结果",
                    result.queryId(),
                    dataSourceName,
                    caliber,
                    resolveXFields(fields, chartType),
                    resolveYFields(fields, chartType),
                    rows
            ));
            case LINE -> List.of(ChartSpec.line(
                    "chart-" + UUID.randomUUID().toString().substring(0, 8),
                    "数据查询结果",
                    result.queryId(),
                    dataSourceName,
                    caliber,
                    resolveXFields(fields, chartType),
                    resolveYFields(fields, chartType),
                    rows
            ));
            case TABLE -> List.of(ChartSpec.table(
                    "chart-" + UUID.randomUUID().toString().substring(0, 8),
                    "数据查询结果",
                    result.queryId(),
                    dataSourceName,
                    caliber,
                    fields,
                    rows
            ));
            default -> List.of();
        };
    }

    /**
     * 根据字段名推断图表类型。
     */
    public ChartType inferChartType(List<String> fields, List<Map<String, Object>> rows) {
        if (fields == null || fields.isEmpty() || rows == null || rows.isEmpty()) {
            return null;
        }

        String lowerFields = String.join(" ", fields).toLowerCase();

        // 时间维度 → 折线图
        if (containsAny(lowerFields, chartProperties.timeFieldKeywords())) {
            return ChartType.LINE;
        }

        // 类别维度 → 柱状图
        if (containsAny(lowerFields, chartProperties.dimensionFieldKeywords())) {
            return ChartType.BAR;
        }

        // 占比分析（类别 <= 5）
        if (rows.size() <= 5 && shouldBePie(fields, rows)) {
            return ChartType.PIE;
        }

        // 默认柱状图（维度分布）
        if (rows.size() >= 2) {
            return ChartType.BAR;
        }

        return null;
    }

    /**
     * 判断是否应生成饼图（占比分析）。
     */
    public boolean shouldBePie(List<String> fields, List<Map<String, Object>> rows) {
        if (fields == null || rows == null || rows.isEmpty()) {
            return false;
        }
        String lowerFields = String.join(" ", fields).toLowerCase();
        if (containsAny(lowerFields, chartProperties.proportionKeywords())) {
            return true;
        }
        // 类别名简短时倾向饼图
        Object firstCol = rows.get(0).keySet().iterator().next();
        return rows.stream().allMatch(row -> {
            Object val = row.get(firstCol);
            return val != null && val.toString().length() < 20;
        });
    }

    /**
     * 推断 X 轴字段。
     */
    public List<String> resolveXFields(List<String> fields, ChartType chartType) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }
        return switch (chartType) {
            case LINE -> fields.stream()
                    .filter(f -> containsAny(f.toLowerCase(), chartProperties.timeFieldKeywords()))
                    .findFirst()
                    .map(List::of)
                    .orElseGet(() -> List.of(fields.get(0)));
            case BAR, PIE, MAP, SCATTER -> {
                String nameField = fields.stream()
                        .filter(f -> !containsAny(f.toLowerCase(), chartProperties.metricFieldKeywords()))
                        .findFirst()
                        .orElseGet(() -> fields.get(0));
                yield List.of(nameField);
            }
            case TABLE -> fields;
        };
    }

    /**
     * 推断 Y 轴字段（数值列）。
     */
    public List<String> resolveYFields(List<String> fields, ChartType chartType) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }
        return fields.stream()
                .filter(f -> containsAny(f.toLowerCase(), chartProperties.metricFieldKeywords()))
                .findFirst()
                .map(f -> List.of(f))
                .orElse(List.of());
    }

    /**
     * 构建图表标题。
     */
    public String buildTitle(String metricName, ChartType chartType) {
        if (metricName == null || metricName.isBlank()) {
            return chartType.name() + " Chart";
        }
        return switch (chartType) {
            case LINE -> metricName + " 变化趋势";
            case PIE -> metricName + " 占比分布";
            case TABLE -> metricName + " 明细数据";
            default -> metricName + " 分布统计";
        };
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) {
                continue;
            }
            if (text.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}