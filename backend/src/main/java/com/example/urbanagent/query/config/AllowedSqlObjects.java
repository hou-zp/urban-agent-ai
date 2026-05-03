package com.example.urbanagent.query.config;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SQL 白名单配置。定义允许查询的表名、列名、维度、聚合函数。
 * 与 {@link com.example.urbanagent.query.application.SemanticQueryCompiler} 配合使用，
 * 确保 NL2SQL 生成的 SQL 不绕过受控编译层。
 *
 * <p>白名单来源优先级：
 * <ol>
 *   <li>DataTableRepository 中的已注册表</li>
 *   <li>ai_metric_def 表中 fact_table 字段引用的表</li>
 *   <li>本类中硬编码的系统表</li>
 * </ol>
 *
 * <p>实际生产中建议将白名单从配置文件加载（yml/properties），
 * 并通过 Flyway 迁移脚本从元数据表同步到缓存。
 */
@Component
public class AllowedSqlObjects {

    /**
     * 允许参与 SELECT、WHERE、GROUP BY、ORDER BY 的表名（已小写）。
     * 包含事实表、维度视图和系统汇总表。
     */
    private static final Set<String> ALLOWED_TABLES = Set.of(
            // 事实表
            "fact_case_event",
            "fact_inspection",
            "fact_complaint",
            "fact_oil_fume_monitoring",
            "fact_garbage_collection",
            "fact_river_surveillance",
            "fact_advertisement_enforcement",
            "fact_leisure_space",
            // 维度视图
            "dim_street",
            "dim_district",
            "dim_grid",
            "dim_merchant",
            "dim_land_plot",
            "dim_unit",
            "dim_scene",
            // 汇总表
            "agg_daily_case",
            "agg_monthly_complaint",
            "agg_oil_fume_warning"
    );

    /**
     * 允许在 SQL 中使用的列名（已小写）。
     * 按表分组，同名列在不同表中可重复出现。
     */
    private static final Map<String, Set<String>> ALLOWED_COLUMNS = Map.ofEntries(
            Map.entry("fact_case_event", Set.of(
                    "event_id", "event_type", "accept_time", "district_code", "district_name",
                    "street_code", "street_name", "grid_code", "grid_name",
                    "case_status", "priority_level", "source_channel",
                    "handler_dept", "responsible_unit", "disposal_time",
                    "abnormal_flag", "overdue_flag", "reoccurrence_flag"
            )),
            Map.entry("fact_inspection", Set.of(
                    "inspection_id", "inspection_type", "inspect_time", "inspector_id",
                    "district_code", "street_code", "grid_code",
                    "problem_found", "problem_count", "scene_code"
            )),
            Map.entry("fact_complaint", Set.of(
                    "complaint_id", "complaint_type", "accept_time", "district_code", "district_name",
                    "street_code", "street_name", "reporter_type",
                    "processing_status", "satisfaction_score"
            )),
            Map.entry("fact_oil_fume_monitoring", Set.of(
                    "monitor_id", "unit_name", "unit_address", "district_code", "street_name",
                    "monitor_time", "concentration_value", "threshold_value",
                    "is_exceed", "is_closed", "closed_time", "warning_level"
            )),
            Map.entry("fact_garbage_collection", Set.of(
                    "collection_id", "location_type", "district_code", "street_name", "grid_name",
                    "collection_time", "weight_kg", "container_type", "overflow_flag"
            )),
            Map.entry("agg_daily_case", Set.of(
                    "stat_date", "district_code", "street_code", "scene_code",
                    "event_count", "abnormal_count", "overdue_count", "avg_disposal_hours"
            )),
            Map.entry("agg_monthly_complaint", Set.of(
                    "stat_month", "district_code", "complaint_type",
                    "total_count", "resolved_count", "avg_response_hours"
            )),
            Map.entry("dim_street", Set.of(
                    "street_code", "street_name", "district_code", "district_name",
                    "governor_name", "contact_phone"
            )),
            Map.entry("dim_merchant", Set.of(
                    "merchant_id", "merchant_name", "business_type", "district_code",
                    "street_name", "license_status", "has_oil_fume_equipment"
            ))
    );

    /**
     * 允许使用的聚合函数。
     */
    private static final Set<String> ALLOWED_FUNCTIONS = Set.of(
            "count", "count_big", "sum", "avg", "max", "min",
            "coalesce", "round", "floor", "ceil", "abs",
            "date_trunc", "to_char", "date_part",
            "concat", "length", "lower", "upper", "trim",
            "now", "current_date", "current_timestamp"
    );

    /**
     * 允许作为 GROUP BY / ORDER BY 的维度列。
     */
    private static final Set<String> ALLOWED_DIMENSIONS = Set.of(
            "district_name", "district_code",
            "street_name", "street_code",
            "grid_name", "grid_code",
            "scene_code", "event_type", "case_status",
            "priority_level", "source_channel",
            "warning_level", "unit_name",
            "stat_date", "stat_month",
            "month", "week", "day"
    );

    /**
     * 允许作为 ORDER BY 方向的值。
     */
    private static final Set<String> ALLOWED_SORT_DIRECTION = Set.of("asc", "desc");

    public boolean isTableAllowed(String tableName) {
        if (tableName == null) return false;
        return ALLOWED_TABLES.contains(tableName.toLowerCase(Locale.ROOT));
    }

    public boolean isColumnAllowed(String tableName, String columnName) {
        if (tableName == null || columnName == null) return false;
        Set<String> tableColumns = ALLOWED_COLUMNS.get(tableName.toLowerCase(Locale.ROOT));
        if (tableColumns == null) return false;
        return tableColumns.contains(columnName.toLowerCase(Locale.ROOT));
    }

    public boolean isFunctionAllowed(String functionName) {
        if (functionName == null) return false;
        return ALLOWED_FUNCTIONS.contains(functionName.toLowerCase(Locale.ROOT));
    }

    public boolean isDimensionAllowed(String dimension) {
        if (dimension == null) return false;
        return ALLOWED_DIMENSIONS.contains(dimension.toLowerCase(Locale.ROOT));
    }

    public boolean isSortDirectionAllowed(String direction) {
        if (direction == null) return false;
        return ALLOWED_SORT_DIRECTION.contains(direction.toLowerCase(Locale.ROOT));
    }

    public Set<String> allowedTables() {
        return ALLOWED_TABLES;
    }

    public Set<String> allowedDimensions() {
        return ALLOWED_DIMENSIONS;
    }

    public Set<String> allowedFunctions() {
        return ALLOWED_FUNCTIONS;
    }
}