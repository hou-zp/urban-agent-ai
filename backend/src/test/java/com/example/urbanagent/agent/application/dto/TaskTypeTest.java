package com.example.urbanagent.agent.application.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskTypeTest {

    @Test
    void shouldContainAllRequiredTaskTypes() {
        // 扩展后应有 11 种任务类型
        assertEquals(13, TaskType.values().length);
    }

    @Test
    void shouldIncludeQuestionAnalysisTask() {
        assertNotNull(TaskType.QUESTION_ANALYSIS);
    }

    @Test
    void shouldIncludeDataQueryTasks() {
        assertNotNull(TaskType.DATA_QUERY_PREPARE);
        assertNotNull(TaskType.DATA_QUERY_EXECUTE);
        assertNotNull(TaskType.METRIC_QUERY);
        assertNotNull(TaskType.BUSINESS_RECORD_QUERY);
    }

    @Test
    void shouldIncludeKnowledgeSearchTasks() {
        assertNotNull(TaskType.KNOWLEDGE_RETRIEVE);
        assertNotNull(TaskType.POLICY_SEARCH);
        assertNotNull(TaskType.LAW_SEARCH);
        assertNotNull(TaskType.BUSINESS_RULE_SEARCH);
    }

    @Test
    void shouldIncludeAnswerAndChartTasks() {
        assertNotNull(TaskType.ANSWER_COMPOSE);
        assertNotNull(TaskType.CHART_GENERATION);
    }

    @Test
    void shouldIncludeSecurityTasks() {
        assertNotNull(TaskType.RISK_REVIEW);
        assertNotNull(TaskType.GUARDRAIL_CHECK);
    }

    @Test
    void shouldSupportMetricQueryTaskType() {
        // 指标查询是智能问数的核心任务
        TaskType task = TaskType.METRIC_QUERY;
        assertNotNull(task);
        assertEquals("METRIC_QUERY", task.name());
    }

    @Test
    void shouldSupportPolicySearchTaskType() {
        // 政策检索是政策解读的核心任务
        TaskType task = TaskType.POLICY_SEARCH;
        assertNotNull(task);
        assertEquals("POLICY_SEARCH", task.name());
    }

    @Test
    void shouldSupportLawSearchTaskType() {
        // 法规检索是法律法规咨询的核心任务
        TaskType task = TaskType.LAW_SEARCH;
        assertNotNull(task);
        assertEquals("LAW_SEARCH", task.name());
    }

    @Test
    void shouldSupportGuardrailCheckTaskType() {
        // 闸门检查是防造假的核心任务
        TaskType task = TaskType.GUARDRAIL_CHECK;
        assertNotNull(task);
        assertEquals("GUARDRAIL_CHECK", task.name());
    }
}