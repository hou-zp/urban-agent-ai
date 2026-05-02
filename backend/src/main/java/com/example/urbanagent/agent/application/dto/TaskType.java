package com.example.urbanagent.agent.application.dto;

/**
 * 智能体任务类型枚举。
 * 覆盖问题分析、数据查询、知识检索、图表生成、风险复核等全链路任务。
 */
public enum TaskType {
    // 问题理解
    QUESTION_ANALYSIS,     // 问题解析（意图识别、槽位抽取）

    // 数据查询
    DATA_QUERY_PREPARE,    // 数据查询准备（指标、维度、SQL 生成）
    DATA_QUERY_EXECUTE,    // 数据查询执行（校验、权限改写、执行）
    METRIC_QUERY,          // 指标查询（聚合统计类）
    BUSINESS_RECORD_QUERY, // 业务记录查询（明细、台账类）

    // 知识检索
    KNOWLEDGE_RETRIEVE,    // 知识检索（综合）
    POLICY_SEARCH,        // 政策检索（专项方案、考核要求）
    LAW_SEARCH,           // 法规检索（违法认定、处罚依据）
    BUSINESS_RULE_SEARCH, // 业务规则检索（流程、归口、处置建议）

    // 答案生成
    ANSWER_COMPOSE,       // 答案融合（六段式结构）
    CHART_GENERATION,      // 图表生成

    // 安全与风险
    RISK_REVIEW,          // 风险复核（高风险问题法制审核）
    GUARDRAIL_CHECK       // 闸门检查（防造假、权限、来源验证）
}
