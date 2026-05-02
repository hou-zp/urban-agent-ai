package com.example.urbanagent.agent.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 图表规范。
 * 用于描述图表的元数据，确保图表数据可追溯。
 */
public record ChartSpec(
        String chartId,           // 图表唯一标识
        ChartType chartType,      // 图表类型
        String title,            // 图表标题
        String queryId,          // 关联的查询编号（必须）
        String dataSourceName,   // 数据来源名称
        String caliber,          // 统计口径
        Instant dataUpdatedAt,   // 数据更新时间
        List<String> xFields,   // X 轴字段
        List<String> yFields,   // Y 轴字段
        List<Map<String, Object>> dataset  // 图表数据（必须来自查询结果）
) {
    /**
     * 图表类型。
     */
    public enum ChartType {
        BAR,        // 柱状图
        LINE,       // 折线图
        PIE,        // 饼图
        TABLE,      // 表格
        MAP,        // 地图
        SCATTER     // 散点图
    }

    /**
     * 检查图表数据是否来自查询结果。
     */
    public boolean hasValidSource() {
        return queryId != null && !queryId.isBlank()
                && dataset != null && !dataset.isEmpty();
    }

    /**
     * 创建柱状图规范。
     */
    public static ChartSpec bar(String chartId, String title, String queryId,
                                String dataSourceName, String caliber,
                                List<String> xFields, List<String> yFields,
                                List<Map<String, Object>> dataset) {
        return new ChartSpec(
                chartId, ChartType.BAR, title, queryId, dataSourceName, caliber,
                Instant.now(), xFields, yFields, dataset
        );
    }

    /**
     * 创建折线图规范。
     */
    public static ChartSpec line(String chartId, String title, String queryId,
                                 String dataSourceName, String caliber,
                                 List<String> xFields, List<String> yFields,
                                 List<Map<String, Object>> dataset) {
        return new ChartSpec(
                chartId, ChartType.LINE, title, queryId, dataSourceName, caliber,
                Instant.now(), xFields, yFields, dataset
        );
    }

    /**
     * 创建表格规范。
     */
    public static ChartSpec table(String chartId, String title, String queryId,
                                  String dataSourceName, String caliber,
                                  List<String> fields,
                                  List<Map<String, Object>> dataset) {
        return new ChartSpec(
                chartId, ChartType.TABLE, title, queryId, dataSourceName, caliber,
                Instant.now(), fields, List.of(), dataset
        );
    }
}