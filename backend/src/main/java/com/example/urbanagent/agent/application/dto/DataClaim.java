package com.example.urbanagent.agent.application.dto;

/**
 * 数据声明结构。
 * 用于追踪答案中每个数据性结论的来源，支持防造假闸门验证。
 */
public record DataClaim(
        String claimText,       // 声明文本，如"垃圾满溢案件数较上月上升15%"
        ClaimType claimType,    // 声明类型
        String queryId,         // 关联的查询编号
        boolean supported       // 是否已通过验证
) {
    public enum ClaimType {
        COUNT,      // 数量统计
        RANKING,     // 排名
        TREND,       // 趋势（上升、下降）
        COMPARISON,  // 对比（同比、环比）
        STATUS,      // 状态
        CHART,       // 图表
        RESPONSIBILITY // 责任单位
    }

    public boolean hasQueryId() {
        return queryId != null && !queryId.isBlank();
    }

    public boolean isVerifiable() {
        return hasQueryId() && supported;
    }
}
