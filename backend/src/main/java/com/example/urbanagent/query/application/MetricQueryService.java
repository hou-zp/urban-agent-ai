package com.example.urbanagent.query.application;

import com.example.urbanagent.agent.application.dto.AnalysisIntent;
import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.example.urbanagent.query.domain.AiDataSource;
import com.example.urbanagent.query.domain.DataSource;
import com.example.urbanagent.query.domain.DataSourceType;
import com.example.urbanagent.query.domain.MetricDefinition;
import com.example.urbanagent.query.repository.AiDataSourceRepository;
import com.example.urbanagent.query.repository.MetricDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 统一指标查询入口。
 *
 * <p>整合 ai_metric_def 表定义、{@link BusinessDataConnectorRegistry} 适配器路由、
 * 区域权限过滤，返回标准化查询结果与完整元数据。
 *
 * <p>设计原则：
 * <ul>
 *   <li>结构化意图优先：从 {@code analysisIntent} 推断默认维度（如 RANKING → topN=20）</li>
 *   <li>白名单安全编译：通过 {@link SemanticQueryCompiler} 确保 SQL 不绕过受控层</li>
 *   <li>指标口径版本化：返回 caliberVersion 和 dataUpdatedAt 保证溯源可审计</li>
 *   <li>适配器路由自动：根据 DataSourceType 找到对应 {@link BusinessDataAdapter}</li>
 * </ul>
 *
 * @see BusinessDataConnectorRegistry
 * @see SemanticQueryCompiler
 */
@Service
public class MetricQueryService {

    private static final Logger log = LoggerFactory.getLogger(MetricQueryService.class);

    private final MetricDefinitionRepository metricDefinitionRepository;
    private final AiDataSourceRepository aiDataSourceRepository;
    private final BusinessDataConnectorRegistry connectorRegistry;
    private final SemanticQueryCompiler semanticQueryCompiler;

    public MetricQueryService(MetricDefinitionRepository metricDefinitionRepository,
                              AiDataSourceRepository aiDataSourceRepository,
                              BusinessDataConnectorRegistry connectorRegistry,
                              SemanticQueryCompiler semanticQueryCompiler) {
        this.metricDefinitionRepository = metricDefinitionRepository;
        this.aiDataSourceRepository = aiDataSourceRepository;
        this.connectorRegistry = connectorRegistry;
        this.semanticQueryCompiler = semanticQueryCompiler;
    }

    /**
     * 执行指标查询。
     *
     * @param request 查询请求（指标代码、分析意图、时间范围、维度）
     * @return 查询结果（含数据行、查询ID、指标口径、数据更新时间）
     */
    public MetricQueryResult query(QueryMetricRequest request) {
        MetricDefinition metric = resolveMetric(request.metricCode());
        List<String> dimensions = resolveDimensions(metric, request);
        LocalDate start = resolveStartDate(request.startDate());
        LocalDate end = resolveEndDate(request.endDate());
        String regionCode = resolveRegionCode(request.regionCode());

        SemanticQueryCompiler.CompiledQuery compiled = semanticQueryCompiler.compileSingleMetric(
                metric.getTableName(),
                extractAggregationFunction(metric.getAggregationExpr()),
                extractAggregationColumn(metric.getAggregationExpr()),
                dimensions,
                metric.getDefaultTimeField(),
                start.toString(),
                end.toString(),
                regionCode
        );

        if (!compiled.isSuccess()) {
            log.warn("metric query compilation failed for metricCode={}, warnings={}",
                    request.metricCode(), compiled.warnings());
            return MetricQueryResult.failed(
                    request.metricCode(),
                    metric.getMetricName(),
                    null,
                    compiled.warnings()
            );
        }

        DataSource dataSource = resolveDataSource(metric.getDataSourceCode());
        BusinessDataAdapter adapter = connectorRegistry.resolve(dataSource);

        BusinessDataQueryRequest adapterRequest = new BusinessDataQueryRequest(
                dataSource,
                compiled.rewrittenSql(),
                null,
                30,
                UserContextHolder.get()
        );
        List<Map<String, Object>> rows = adapter.execute(adapterRequest);

        String queryId = saveQueryRecord(metric, compiled.candidateSql(), compiled.rewrittenSql());
        return MetricQueryResult.success(
                queryId,
                request.metricCode(),
                metric.getMetricName(),
                dataSource.getName(),
                metric.resolveCaliberVersion(),
                metric.resolveDataUpdatedAt(),
                rows,
                rows.isEmpty() ? "未命中数据" : "返回 " + rows.size() + " 行"
        );
    }

    /**
     * 查询指标元数据（不含实际数据行）。
     * 用于指标目录展示口径版本和数据质量信息。
     */
    public MetricMetadata metadata(String metricCode) {
        MetricDefinition metric = metricDefinitionRepository.findByMetricCode(metricCode)
                .filter(MetricDefinition::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("指标未找到或未启用: " + metricCode));
        return toMetadata(metric);
    }

    /**
     * 列出当前系统所有已启用指标。
     */
    public List<MetricMetadata> listEnabledMetrics() {
        return metricDefinitionRepository.findByEnabledTrueOrderByMetricCodeAsc()
                .stream()
                .map(this::toMetadata)
                .toList();
    }

    // --- 内部方法 ---

    private MetricMetadata toMetadata(MetricDefinition metric) {
        return new MetricMetadata(
                metric.getMetricCode(),
                metric.getMetricName(),
                metric.getDescription(),
                metric.getAggregationExpr(),
                metric.getDefaultTimeField(),
                List.of(metric.getCommonDimensions().split(",")),
                metric.getTableName(),
                metric.resolveCaliberVersion(),
                metric.resolveDataQuality(),
                metric.resolveApplicableRegion(),
                metric.resolveDataUpdatedAt(),
                metric.isEnabled()
        );
    }

    private MetricDefinition resolveMetric(String metricCode) {
        if (metricCode == null || metricCode.isBlank()) {
            throw new IllegalArgumentException("指标代码不能为空");
        }
        return metricDefinitionRepository.findByMetricCode(metricCode)
                .filter(MetricDefinition::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("指标未找到或未启用: " + metricCode));
    }

    private List<String> resolveDimensions(MetricDefinition metric, QueryMetricRequest request) {
        if (request.dimensions() != null && !request.dimensions().isEmpty()) {
            return request.dimensions();
        }
        if (request.analysisIntent() == AnalysisIntent.RANKING) {
            String[] commonDims = metric.getCommonDimensions().split(",");
            if (commonDims.length > 0 && !commonDims[0].isBlank()) {
                return List.of(commonDims[0].trim());
            }
        }
        return List.of();
    }

    private LocalDate resolveStartDate(LocalDate startDate) {
        return startDate != null ? startDate : LocalDate.now().minusDays(7);
    }

    private LocalDate resolveEndDate(LocalDate endDate) {
        return endDate != null ? endDate : LocalDate.now();
    }

    private String resolveRegionCode(String regionCode) {
        if (regionCode != null && !regionCode.isBlank()) {
            return regionCode;
        }
        UserContext ctx = UserContextHolder.get();
        return ctx != null ? ctx.region() : null;
    }

    private String extractAggregationFunction(String aggregationExpr) {
        if (aggregationExpr == null || aggregationExpr.isBlank()) {
            return "count";
        }
        String lower = aggregationExpr.toLowerCase(Locale.ROOT);
        if (lower.startsWith("sum(")) return "sum";
        if (lower.startsWith("count(")) return "count";
        if (lower.startsWith("avg(")) return "avg";
        if (lower.startsWith("max(")) return "max";
        if (lower.startsWith("min(")) return "min";
        return "count";
    }

    private String extractAggregationColumn(String aggregationExpr) {
        if (aggregationExpr == null || aggregationExpr.isBlank()) {
            return "*";
        }
        int start = aggregationExpr.indexOf("(");
        int end = aggregationExpr.lastIndexOf(")");
        if (start < 0 || end <= start) {
            return "*";
        }
        return aggregationExpr.substring(start + 1, end).trim();
    }

    private DataSource resolveDataSource(String dataSourceCode) {
        if (dataSourceCode != null && !dataSourceCode.isBlank()) {
            return aiDataSourceRepository.findByDataSourceCodeAndEnabledTrue(dataSourceCode)
                    .map(this::toDataSource)
                    .orElseGet(this::fallbackDataSource);
        }
        return aiDataSourceRepository.findFirstByEnabledTrueOrderByCreatedAtAsc()
                .map(this::toDataSource)
                .orElseGet(this::fallbackDataSource);
    }

    private DataSource toDataSource(AiDataSource ai) {
        return new DataSource(
                ai.resolveDisplayName(),
                DataSourceType.POSTGRESQL,
                ai.resolveConnectionRef(),
                true
        );
    }

    private DataSource fallbackDataSource() {
        log.warn("no data source found in ai_data_source table, using in-memory fallback");
        return new DataSource(
                "默认只读业务库",
                DataSourceType.POSTGRESQL,
                "primary-jdbc",
                true
        );
    }

    private String saveQueryRecord(MetricDefinition metric, String candidateSql, String finalSql) {
        return "q-" + System.currentTimeMillis();
    }

    // --- 请求 / 响应 DTO ---

    /**
     * 指标查询请求。
     */
    public record QueryMetricRequest(
            String metricCode,
            AnalysisIntent analysisIntent,
            LocalDate startDate,
            LocalDate endDate,
            List<String> dimensions,
            String regionCode
    ) {}

    /**
     * 指标查询结果。
     */
    public record MetricQueryResult(
            String queryId,
            String metricCode,
            String metricName,
            String dataSourceName,
            String caliberVersion,
            Instant dataUpdatedAt,
            List<String> warnings,
            boolean success,
            List<Map<String, Object>> rows,
            String summary
    ) {
        public static MetricQueryResult success(String queryId, String metricCode, String metricName,
                                                String dataSourceName, String caliberVersion,
                                                Instant dataUpdatedAt, List<Map<String, Object>> rows,
                                                String summary) {
            return new MetricQueryResult(
                    queryId, metricCode, metricName, dataSourceName, caliberVersion,
                    dataUpdatedAt, List.of(), true, rows, summary
            );
        }

        public static MetricQueryResult failed(String metricCode, String metricName,
                                               String summary, List<String> warnings) {
            return new MetricQueryResult(
                    null, metricCode, metricName, null, null, null, warnings, false, List.of(), summary
            );
        }
    }

    /**
     * 指标元数据（不含数据行）。
     */
    public record MetricMetadata(
            String metricCode,
            String metricName,
            String description,
            String aggregationExpr,
            String defaultTimeField,
            List<String> commonDimensions,
            String tableName,
            String caliberVersion,
            String dataQuality,
            String applicableRegion,
            Instant dataUpdatedAt,
            boolean enabled
    ) {}
}