package com.example.urbanagent.agent.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 图表推断规则配置。
 *
 * <p>字段名关键词用于从查询结果字段推断图表类型。
 * 修改关键词后无需重新编译，直接修改 application.yml 即可生效。
 */
@ConfigurationProperties(prefix = "urban-agent.chart")
public record ChartProperties(
        /** 识别为时间维度的字段名关键词（触发折线图） */
        List<String> timeFieldKeywords,
        /** 识别为类别维度的字段名关键词（触发柱状图） */
        List<String> dimensionFieldKeywords,
        /** 饼图判断关键词（含占比语义时触发） */
        List<String> proportionKeywords,
        /** Y 轴数值字段关键词 */
        List<String> metricFieldKeywords
) {
    public static final List<String> DEFAULT_TIME_KEYWORDS = List.of(
            "date", "month", "week", "day", "stat_date", "stat_month", "time", "period"
    );
    public static final List<String> DEFAULT_DIMENSION_KEYWORDS = List.of(
            "street", "district", "unit", "category", "name", "type", "scene", "scene_code"
    );
    public static final List<String> DEFAULT_PROPORTION_KEYWORDS = List.of(
            "percent", "ratio", "proportion", "占比", "比例"
    );
    public static final List<String> DEFAULT_METRIC_KEYWORDS = List.of(
            "value", "count", "metric", "total", "avg", "metric_value"
    );

    public ChartProperties {
        if (timeFieldKeywords == null || timeFieldKeywords.isEmpty()) {
            timeFieldKeywords = DEFAULT_TIME_KEYWORDS;
        }
        if (dimensionFieldKeywords == null || dimensionFieldKeywords.isEmpty()) {
            dimensionFieldKeywords = DEFAULT_DIMENSION_KEYWORDS;
        }
        if (proportionKeywords == null || proportionKeywords.isEmpty()) {
            proportionKeywords = DEFAULT_PROPORTION_KEYWORDS;
        }
        if (metricFieldKeywords == null || metricFieldKeywords.isEmpty()) {
            metricFieldKeywords = DEFAULT_METRIC_KEYWORDS;
        }
    }
}