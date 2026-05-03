package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.AgentPlanGraph;
import com.example.urbanagent.agent.application.dto.ParsedQuestion;
import com.example.urbanagent.agent.application.dto.ChartSpec;
import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.query.application.MetricQueryService.MetricQueryResult;
import com.example.urbanagent.query.application.dto.QueryCardView;
import com.example.urbanagent.knowledge.application.dto.KnowledgeSearchHit;
import com.example.urbanagent.agent.application.dto.EvidenceRef;
import com.example.urbanagent.agent.application.dto.DataClaim;
import com.example.urbanagent.agent.application.dto.AnswerDraft;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 智能体执行上下文。
 *
 * <p>统一管理一次对话请求的所有状态：用户信息、解析结果、任务计划、工具结果、证据引用。
 * 由编排器（ChatApplicationService / PlanApplicationService）创建，
 * 在任务执行链路中传递，被 AnswerComposer 和 FinalGuardrailService 使用。
 *
 * <p>设计原则：
 * <ul>
 *   <li>单次请求一个实例，请求结束后丢弃</li>
 *   <li>只存储结构化结果，不存储原始 AI 输出</li>
 *   <li>taskResults 用于非结构化中间结果；dataResults / policyResults 用于可追溯结构化结果</li>
 * </ul>
 *
 * @see IntentAnalysisService
 * @see PlanApplicationService
 * @see AnswerComposer
 * @see FinalGuardrailService
 */
public class AgentContext {

    private final String id;
    private final String conversationId;
    private final String originalQuestion;
    private final Instant createdAt = Instant.now();

    private UserContext userContext;
    private ParsedQuestion parsedQuestion;
    private AgentPlanGraph plan;

    /** 任务执行结果（taskId → 结果对象） */
    private final Map<String, Object> taskResults = new LinkedHashMap<>();

    /** 数据查询结果列表 */
    private final List<Object> dataResults = new ArrayList<>();

    /** 政策检索结果列表 */
    private final List<KnowledgeSearchHit> policyResults = new ArrayList<>();

    /** 法规检索结果列表 */
    private final List<KnowledgeSearchHit> lawResults = new ArrayList<>();

    /** 业务规则检索结果 */
    private final Map<String, Object> businessRuleResults = new LinkedHashMap<>();

    /** 图表规格列表 */
    private final List<ChartSpec> chartSpecs = new ArrayList<>();

    /** 证据引用列表 */
    private final List<EvidenceRef> evidenceRefs = new ArrayList<>();

    /** 答案草稿 */
    private AnswerDraft answerDraft;

    private AgentContext(String conversationId, String originalQuestion) {
        this.id = UUID.randomUUID().toString();
        this.conversationId = conversationId;
        this.originalQuestion = originalQuestion;
    }

    public static AgentContext of(String conversationId, String originalQuestion) {
        return new AgentContext(conversationId, originalQuestion);
    }

    public static AgentContext of(String originalQuestion) {
        return new AgentContext(null, originalQuestion);
    }

    // --- 基础字段 ---

    public String id() {
        return id;
    }

    public String conversationId() {
        return conversationId;
    }

    public String originalQuestion() {
        return originalQuestion;
    }

    public Instant createdAt() {
        return createdAt;
    }

    // --- 用户上下文 ---

    public UserContext userContext() {
        return userContext;
    }

    public void setUserContext(UserContext userContext) {
        this.userContext = userContext;
    }

    // --- 解析结果 ---

    public ParsedQuestion parsedQuestion() {
        return parsedQuestion;
    }

    public void setParsedQuestion(ParsedQuestion parsedQuestion) {
        this.parsedQuestion = parsedQuestion;
    }

    public boolean hasParsedQuestion() {
        return parsedQuestion != null;
    }

    // --- 任务计划 ---

    public AgentPlanGraph plan() {
        return plan;
    }

    public void setPlan(AgentPlanGraph plan) {
        this.plan = plan;
    }

    public boolean hasPlan() {
        return plan != null;
    }

    // --- 任务结果 ---

    public Map<String, Object> taskResults() {
        return taskResults;
    }

    public void addTaskResult(String taskId, Object result) {
        this.taskResults.put(taskId, result);
    }

    public Object getTaskResult(String taskId) {
        return this.taskResults.get(taskId);
    }

    @SuppressWarnings("unchecked")
    public <T> T getTaskResultAs(String taskId, Class<T> type) {
        Object result = this.taskResults.get(taskId);
        if (result == null) {
            return null;
        }
        if (type.isInstance(result)) {
            return type.cast(result);
        }
        throw new ClassCastException("Task result for " + taskId + " is not of type " + type.getName());
    }

    // --- 数据查询结果 ---

    public List<Object> dataResults() {
        return dataResults;
    }

    public void addDataResult(Object result) {
        this.dataResults.add(result);
    }

    @SuppressWarnings("unchecked")
    public List<QueryCardView> queryCardResults() {
        return dataResults.stream()
                .filter(r -> r instanceof QueryCardView)
                .map(r -> (QueryCardView) r)
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<MetricQueryResult> metricQueryResults() {
        return dataResults.stream()
                .filter(r -> r instanceof MetricQueryResult)
                .map(r -> (MetricQueryResult) r)
                .toList();
    }

    public boolean hasDataResults() {
        return !dataResults.isEmpty();
    }

    // --- 政策检索结果 ---

    public List<KnowledgeSearchHit> policyResults() {
        return policyResults;
    }

    public void addPolicyResult(KnowledgeSearchHit hit) {
        this.policyResults.add(hit);
    }

    public void addPolicyResults(List<KnowledgeSearchHit> hits) {
        this.policyResults.addAll(hits);
    }

    public boolean hasPolicyResults() {
        return !policyResults.isEmpty();
    }

    // --- 法规检索结果 ---

    public List<KnowledgeSearchHit> lawResults() {
        return lawResults;
    }

    public void addLawResult(KnowledgeSearchHit hit) {
        this.lawResults.add(hit);
    }

    public void addLawResults(List<KnowledgeSearchHit> hits) {
        this.lawResults.addAll(hits);
    }

    public boolean hasLawResults() {
        return !lawResults.isEmpty();
    }

    // --- 业务规则结果 ---

    public Map<String, Object> businessRuleResults() {
        return businessRuleResults;
    }

    public void setBusinessRuleResults(Map<String, Object> results) {
        this.businessRuleResults.clear();
        this.businessRuleResults.putAll(results);
    }

    // --- 图表规格 ---

    public List<ChartSpec> chartSpecs() {
        return chartSpecs;
    }

    public void addChartSpec(ChartSpec spec) {
        this.chartSpecs.add(spec);
    }

    public void addChartSpecs(List<ChartSpec> specs) {
        this.chartSpecs.addAll(specs);
    }

    public boolean hasChartSpecs() {
        return !chartSpecs.isEmpty();
    }

    // --- 证据引用 ---

    public List<EvidenceRef> evidenceRefs() {
        return evidenceRefs;
    }

    public void addEvidenceRef(EvidenceRef ref) {
        this.evidenceRefs.add(ref);
    }

    public void addEvidenceRefs(List<EvidenceRef> refs) {
        this.evidenceRefs.addAll(refs);
    }

    public boolean hasEvidenceRefs() {
        return !evidenceRefs.isEmpty();
    }

    // --- 答案草稿 ---

    public AnswerDraft answerDraft() {
        return answerDraft;
    }

    public void setAnswerDraft(AnswerDraft draft) {
        this.answerDraft = draft;
    }

    public boolean hasAnswerDraft() {
        return answerDraft != null;
    }

    // --- 防造假辅助方法 ---

    /**
     * 检查是否包含无 queryId 绑定的数据声明。
     * 用于 FinalGuardrailService 拦截。
     */
    public boolean hasUnverifiableDataClaims() {
        if (answerDraft == null || answerDraft.dataClaims() == null) {
            return false;
        }
        return answerDraft.dataClaims().stream()
                .anyMatch(claim -> !claim.hasQueryId());
    }

    /**
     * 检查是否包含无法定证据的法规结论。
     */
    public boolean hasUnattributedLegalContent() {
        if (parsedQuestion != null && parsedQuestion.requiresCitation()
                && policyResults.isEmpty() && lawResults.isEmpty() && evidenceRefs.isEmpty()) {
            return true;
        }
        return false;
    }

    /**
     * 获取所有数据查询的 queryId 列表（用于验证数据声明）。
     */
    public List<String> allQueryIds() {
        List<String> ids = new ArrayList<>();
        for (Object result : dataResults) {
            if (result instanceof QueryCardView card) {
                if (card.queryId() != null && !card.queryId().isBlank()) {
                    ids.add(card.queryId());
                }
            }
            if (result instanceof MetricQueryResult metric) {
                if (metric.queryId() != null && !metric.queryId().isBlank()) {
                    ids.add(metric.queryId());
                }
            }
        }
        return ids;
    }
}