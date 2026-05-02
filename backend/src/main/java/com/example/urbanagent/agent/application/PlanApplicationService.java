package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.ComposedAnswer;
import com.example.urbanagent.agent.application.dto.EvidenceRef;
import com.example.urbanagent.agent.application.dto.PlanStepView;
import com.example.urbanagent.agent.application.dto.PlanView;
import com.example.urbanagent.agent.application.dto.AgentPlanGraph;
import com.example.urbanagent.agent.application.dto.AgentTask;
import com.example.urbanagent.agent.application.dto.MessageCitationView;
import com.example.urbanagent.agent.application.dto.ParsedQuestion;
import com.example.urbanagent.agent.application.dto.PlanStepArtifact;
import com.example.urbanagent.agent.application.dto.PlanStepFailureCode;
import com.example.urbanagent.agent.application.dto.PlanStepFailureArtifact;
import com.example.urbanagent.agent.application.dto.SlotType;
import com.example.urbanagent.agent.application.dto.TaskType;
import com.example.urbanagent.agent.domain.AgentMessage;
import com.example.urbanagent.agent.domain.AgentRun;
import com.example.urbanagent.agent.domain.MessageRole;
import com.example.urbanagent.agent.domain.Plan;
import com.example.urbanagent.agent.domain.PlanStatus;
import com.example.urbanagent.agent.domain.PlanStep;
import com.example.urbanagent.agent.repository.AgentMessageRepository;
import com.example.urbanagent.agent.repository.AgentRunRepository;
import com.example.urbanagent.agent.repository.PlanRepository;
import com.example.urbanagent.agent.repository.PlanStepRepository;
import com.example.urbanagent.agent.domain.ToolCall;
import com.example.urbanagent.agent.repository.ToolCallRepository;
import com.example.urbanagent.audit.application.AuditLogService;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.example.urbanagent.knowledge.application.KnowledgeSearchService;
import com.example.urbanagent.knowledge.application.dto.KnowledgeSearchHit;
import com.example.urbanagent.knowledge.domain.KnowledgeCategory;
import com.example.urbanagent.query.application.QueryApplicationService;
import com.example.urbanagent.query.application.OrganizationDimensionTranslator;
import com.example.urbanagent.query.application.ReadonlySqlQueryService;
import com.example.urbanagent.query.application.dto.DataFragment;
import com.example.urbanagent.query.application.dto.DataStatement;
import com.example.urbanagent.query.application.dto.ExecuteQueryRequest;
import com.example.urbanagent.query.application.dto.PreviewQueryRequest;
import com.example.urbanagent.query.application.dto.QueryAnswerView;
import com.example.urbanagent.query.application.dto.QueryCardView;
import com.example.urbanagent.query.application.dto.QueryExecuteView;
import com.example.urbanagent.query.application.dto.QueryPreviewView;
import com.example.urbanagent.query.domain.QueryRecord;
import com.example.urbanagent.query.repository.QueryRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PlanApplicationService {

    private static final Pattern ROW_COUNT_PATTERN = Pattern.compile("返回\\s*(\\d+)\\s*行");

    private final PlanRepository planRepository;
    private final PlanStepRepository planStepRepository;
    private final PlanViewMapper planViewMapper;
    private final QueryApplicationService queryApplicationService;
    private final ReadonlySqlQueryService readonlySqlQueryService;
    private final OrganizationDimensionTranslator organizationDimensionTranslator;
    private final QueryRecordRepository queryRecordRepository;
    private final ToolCallRepository toolCallRepository;
    private final AgentRunRepository agentRunRepository;
    private final AgentMessageRepository agentMessageRepository;
    private final QuestionParsingService questionParsingService;
    private final TaskPlanner taskPlanner;
    private final KnowledgeSearchService knowledgeSearchService;
    private final KnowledgePlanContextService knowledgePlanContextService;
    private final AnswerComposer answerComposer;
    private final FinalGuardrailService finalGuardrailService;
    private final PlanExecutionContextService planExecutionContextService;
    private final PlanStepFailureArtifactMapper planStepFailureArtifactMapper;
    private final PlanRetryAdvisor planRetryAdvisor;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public PlanApplicationService(PlanRepository planRepository,
                                  PlanStepRepository planStepRepository,
                                  PlanViewMapper planViewMapper,
                                  QueryApplicationService queryApplicationService,
                                  ReadonlySqlQueryService readonlySqlQueryService,
                                  OrganizationDimensionTranslator organizationDimensionTranslator,
                                  QueryRecordRepository queryRecordRepository,
                                  ToolCallRepository toolCallRepository,
                                  AgentRunRepository agentRunRepository,
                                  AgentMessageRepository agentMessageRepository,
                                  QuestionParsingService questionParsingService,
                                  TaskPlanner taskPlanner,
                                  KnowledgeSearchService knowledgeSearchService,
                                  KnowledgePlanContextService knowledgePlanContextService,
                                  AnswerComposer answerComposer,
                                  FinalGuardrailService finalGuardrailService,
                                  PlanExecutionContextService planExecutionContextService,
                                  PlanStepFailureArtifactMapper planStepFailureArtifactMapper,
                                  PlanRetryAdvisor planRetryAdvisor,
                                  ObjectMapper objectMapper,
                                  AuditLogService auditLogService) {
        this.planRepository = planRepository;
        this.planStepRepository = planStepRepository;
        this.planViewMapper = planViewMapper;
        this.queryApplicationService = queryApplicationService;
        this.readonlySqlQueryService = readonlySqlQueryService;
        this.organizationDimensionTranslator = organizationDimensionTranslator;
        this.queryRecordRepository = queryRecordRepository;
        this.toolCallRepository = toolCallRepository;
        this.agentRunRepository = agentRunRepository;
        this.agentMessageRepository = agentMessageRepository;
        this.questionParsingService = questionParsingService;
        this.taskPlanner = taskPlanner;
        this.knowledgeSearchService = knowledgeSearchService;
        this.knowledgePlanContextService = knowledgePlanContextService;
        this.answerComposer = answerComposer;
        this.finalGuardrailService = finalGuardrailService;
        this.planExecutionContextService = planExecutionContextService;
        this.planStepFailureArtifactMapper = planStepFailureArtifactMapper;
        this.planRetryAdvisor = planRetryAdvisor;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public PlanView getPlanByRunId(String runId) {
        Plan plan = planRepository.findByRunId(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        return planViewMapper.toView(plan, planStepRepository.findByPlanIdOrderByStepOrderAsc(plan.getId()));
    }

    @Transactional
    public PlanView createPlanIfNeeded(AgentRun run) {
        ParsedQuestion parsedQuestion = questionParsingService.loadOrAnalyze(run.getId(), run.getQuestion());
        if (!isComplexQuestion(parsedQuestion)) {
            return null;
        }
        Optional<Plan> existing = planRepository.findByRunId(run.getId());
        if (existing.isPresent()) {
            Plan plan = existing.get();
            return planViewMapper.toView(plan, planStepRepository.findByPlanIdOrderByStepOrderAsc(plan.getId()));
        }

        Plan plan = new Plan(run.getId(), run.getQuestion().trim());
        plan.start();
        Plan savedPlan = planRepository.save(plan);
        AgentPlanGraph graph = taskPlanner.buildGraph(parsedQuestion);
        List<PlanStep> steps = buildSteps(savedPlan.getId(), graph);
        if (!steps.isEmpty()) {
            PlanStep firstStep = steps.get(0);
            firstStep.start();
            firstStep.complete("已完成结构化问题解析", "parsed-question");
        }
        List<PlanStep> savedSteps = planStepRepository.saveAll(steps);
        return planViewMapper.toView(savedPlan, savedSteps);
    }

    @Transactional
    public PlanView completePlanAfterAnswer(String runId, AgentAnswer answer) {
        Plan plan = planRepository.findByRunId(runId).orElse(null);
        if (plan == null) {
            return null;
        }
        List<PlanStep> steps = planStepRepository.findByPlanIdOrderByStepOrderAsc(plan.getId());
        for (PlanStep step : steps) {
            if (step.getTaskType() == TaskType.KNOWLEDGE_RETRIEVE
                    && step.getStatus() != PlanStatus.COMPLETED
                    && !answer.citations().isEmpty()) {
                step.start();
                step.complete("已检索 " + answer.citations().size() + " 条参考依据", "citations:" + answer.citations().size());
            }
            if (step.getTaskType() == TaskType.ANSWER_COMPOSE && step.getStatus() != PlanStatus.COMPLETED) {
                step.start();
                step.complete(truncate(answer.content(), 120), "answer");
            }
        }
        List<PlanStep> savedSteps = planStepRepository.saveAll(steps);
        if (savedSteps.stream().allMatch(step -> step.getStatus() == PlanStatus.COMPLETED
                || step.getStatus() == PlanStatus.ABANDONED)) {
            plan.complete();
        }
        return planViewMapper.toView(planRepository.save(plan), savedSteps);
    }

    public PlanView executeNextStep(String runId) {
        Plan plan = planRepository.findByRunId(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        if (plan.getStatus() == PlanStatus.COMPLETED || plan.getStatus() == PlanStatus.ABANDONED) {
            return planViewMapper.toView(plan, planStepRepository.findByPlanIdOrderByStepOrderAsc(plan.getId()));
        }

        List<PlanStep> steps = planStepRepository.findByPlanIdOrderByStepOrderAsc(plan.getId());
        PlanStep nextStep = steps.stream()
                .filter(step -> step.getStatus() == PlanStatus.FAILED
                        || step.getStatus() == PlanStatus.TODO
                        || step.getStatus() == PlanStatus.IN_PROGRESS)
                .findFirst()
                .orElse(null);
        if (nextStep == null) {
            plan.complete();
            return planViewMapper.toView(planRepository.save(plan), steps);
        }
        return executeStep(runId, nextStep.getId());
    }

    @Transactional
    public PlanAutoExecutionResult executePlanToAnswer(String runId) {
        PlanView latestPlan = getPlanByRunId(runId);
        int guard = 0;
        while (guard++ < 8 && latestPlan.status() != PlanStatus.COMPLETED.name() && latestPlan.status() != PlanStatus.ABANDONED.name()) {
            latestPlan = executeNextStep(runId);
        }
        if (latestPlan.status() != PlanStatus.COMPLETED.name()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "综合分析任务尚未形成最终回答");
        }
        AgentRun run = agentRunRepository.findByIdWithSessionOwner(runId, UserContextHolder.get().userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));
        AgentMessage answerMessage = agentMessageRepository.findBySessionIdOrderByCreatedAtAsc(run.getSessionId())
                .stream()
                .filter(message -> message.getRole() == MessageRole.ASSISTANT)
                .filter(message -> !message.getCreatedAt().isBefore(run.getCreatedAt()))
                .reduce((left, right) -> right)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "综合分析任务尚未产出回答"));
        return new PlanAutoExecutionResult(latestPlan, answerMessage);
    }

    public PlanView executeStep(String runId, String stepId) {
        Plan plan = planRepository.findByRunId(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        List<PlanStep> steps = planStepRepository.findByPlanIdOrderByStepOrderAsc(plan.getId());
        PlanStep step = steps.stream()
                .filter(item -> item.getId().equals(stepId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_STEP_NOT_FOUND));

        if (step.getStatus() == PlanStatus.COMPLETED || step.getStatus() == PlanStatus.ABANDONED) {
            return planViewMapper.toView(plan, steps);
        }
        if (plan.getStatus() == PlanStatus.ABANDONED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计划已废弃，不能继续执行");
        }
        plan.start();
        planRepository.save(plan);
        try {
            ensureDependenciesReady(runId, plan, steps, step);
            autoRecoverDependencies(runId, plan, steps, step);
            step.start();
            planStepRepository.save(step);
            executeStepBody(runId, plan, steps, step);
        } catch (BusinessException ex) {
            return markStepFailed(runId, plan, steps, step, buildFailureArtifact(ex), "执行失败：" + ex.getMessage());
        } catch (RuntimeException ex) {
            return markStepFailed(runId, plan, steps, step, buildFailureArtifact(ex), "执行失败：" + safeFailureMessage(ex));
        }

        List<PlanStep> savedSteps = planStepRepository.saveAll(steps);
        if (savedSteps.stream().allMatch(item -> item.getStatus() == PlanStatus.COMPLETED || item.getStatus() == PlanStatus.ABANDONED)) {
            plan.complete();
        } else {
            plan.start();
        }
        return planViewMapper.toView(planRepository.save(plan), savedSteps);
    }

    private void autoRecoverDependencies(String runId, Plan plan, List<PlanStep> steps, PlanStep step) {
        List<Integer> rebuildOrders = planRetryAdvisor.requiredDependencyRebuildOrders(step, steps);
        if (rebuildOrders.isEmpty()) {
            return;
        }
        for (Integer stepOrder : rebuildOrders) {
            PlanStep dependency = findStepByOrder(steps, stepOrder)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_STEP_NOT_FOUND));
            recordDependencyRecovery(runId, step, dependency);
            reExecuteDependency(runId, plan, steps, dependency);
        }
    }

    private void reExecuteDependency(String runId, Plan plan, List<PlanStep> steps, PlanStep dependency) {
        if (dependency.getStatus() == PlanStatus.ABANDONED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "前置步骤已废弃，无法自动恢复");
        }
        ensureDependenciesReady(runId, plan, steps, dependency);
        autoRecoverDependencies(runId, plan, steps, dependency);
        dependency.start();
        planStepRepository.save(dependency);
        executeStepBody(runId, plan, steps, dependency);
    }

    private void executeStepBody(String runId, Plan plan, List<PlanStep> steps, PlanStep step) {
        switch (step.getTaskType()) {
            case QUESTION_ANALYSIS -> {
                step.complete("结构化解析已完成", "parsed-question");
                auditLogService.recordPlanStep(step, runId, "plan.step.complete", null, null, step.getOutputSummary());
            }
            case DATA_QUERY_PREPARE -> {
                QueryPreviewView preview = queryApplicationService.preview(new PreviewQueryRequest(plan.getGoal()));
                PlanStepArtifact artifact = new PlanStepArtifact(
                        "query-preview",
                        preview.queryId(),
                        preview.validatedSql(),
                        null,
                        preview.metricCode(),
                        preview.metricName(),
                        preview.permissionRewrite(),
                        null,
                        preview.warnings(),
                        List.of(),
                        preview.summary()
                );
                step.complete(
                        "已生成候选 SQL；" + preview.summary() + "；权限改写：" + preview.permissionRewrite(),
                        preview.queryId() == null ? "preview-sql" : "query-preview:" + preview.queryId(),
                        planExecutionContextService.write(artifact)
                );
                auditLogService.recordPlanStep(step, runId, "plan.step.complete", preview.queryId(), null, step.getOutputSummary());
                saveToolCall(runId, step.getId(), "plan.data_query_preview", truncate(plan.getGoal(), 200), preview.validatedSql(), preview.queryId(), null);
            }
            case DATA_QUERY_EXECUTE -> {
                String sql = planExecutionContextService.requirePreparedSql(steps);
                QueryExecuteView executeView = queryApplicationService.execute(new ExecuteQueryRequest(plan.getGoal(), sql));
                PlanStepArtifact artifact = new PlanStepArtifact(
                        "query-execute",
                        executeView.queryId(),
                        null,
                        executeView.executedSql(),
                        null,
                        null,
                        null,
                        executeView.rowCount(),
                        List.of(),
                        List.of(),
                        executeView.summary()
                );
                step.complete(
                        "已执行只读查询，返回 " + executeView.rowCount() + " 行；" + summarizeRows(executeView),
                        executeView.queryId() == null ? "row-count:" + executeView.rowCount() : "query:" + executeView.queryId(),
                        planExecutionContextService.write(artifact)
                );
                auditLogService.recordPlanStep(step, runId, "plan.step.complete", executeView.queryId(), null, step.getOutputSummary());
                saveToolCall(runId, step.getId(), "plan.readonly_sql_query", truncate(sql, 200), truncate(executeView.summary(), 200), executeView.queryId(), null);
            }
            case KNOWLEDGE_RETRIEVE -> {
                ParsedQuestion parsedQuestion = questionParsingService.loadOrAnalyze(runId, plan.getGoal());
                KnowledgeCategory preferredCategory = parsedQuestion.preferredKnowledgeCategory().orElse(null);
                List<KnowledgeSearchHit> hits = knowledgeSearchService.search(plan.getGoal(), preferredCategory, 4);
                String summary = hits.isEmpty()
                        ? "未检索到可直接引用的依据"
                        : "已检索 " + hits.size() + " 条依据，首条为《" + hits.get(0).documentTitle() + "》";
                PlanStepArtifact artifact = new PlanStepArtifact(
                        "knowledge-hits",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        hits.stream().map(KnowledgeSearchHit::documentId).toList(),
                        summary
                );
                step.complete(summary, "knowledge:" + hits.size(), planExecutionContextService.write(artifact));
                String evidenceId = hits.isEmpty() ? null : hits.get(0).documentId();
                auditLogService.recordPlanStep(step, runId, "plan.step.complete", null, evidenceId, step.getOutputSummary());
                saveToolCall(runId, step.getId(), "plan.knowledge_retrieve", truncate(plan.getGoal(), 200), truncate(summary, 200), null, evidenceId);
            }
            case ANSWER_COMPOSE -> {
                AgentAnswer answer = buildPlanAnswer(runId, plan, steps);
                PlanStepArtifact artifact = new PlanStepArtifact(
                        "answer-compose",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        answer.composedAnswer() == null ? null : answer.composedAnswer().queryCards().size(),
                        List.of(),
                        answer.citations().stream().map(MessageCitationView::documentId).distinct().toList(),
                        truncate(answer.content(), 200)
                );
                step.complete(
                        truncate(answer.content(), 120),
                        answer.composedAnswer() == null ? "answer-blocked" : "answer",
                        planExecutionContextService.write(artifact)
                );
                String evidenceId = answer.citations().isEmpty() ? null : answer.citations().get(0).documentId();
                auditLogService.recordPlanStep(step, runId, "plan.step.complete", null, evidenceId, step.getOutputSummary());
                saveToolCall(runId, step.getId(), "plan.answer_compose", truncate(plan.getGoal(), 200), truncate(answer.content(), 200), null, evidenceId);
                savePlanAnswerMessage(runId, answer);
            }
        }
    }

    @Transactional
    public void abandonPlan(String runId, String reason) {
        Plan plan = planRepository.findByRunId(runId).orElse(null);
        if (plan == null) {
            return;
        }
        List<PlanStep> steps = planStepRepository.findByPlanIdOrderByStepOrderAsc(plan.getId());
        for (PlanStep step : steps) {
            if (step.getStatus() == PlanStatus.TODO || step.getStatus() == PlanStatus.IN_PROGRESS) {
                step.abandon(reason);
            }
        }
        plan.abandon();
        planStepRepository.saveAll(steps);
        planRepository.save(plan);
    }

    public boolean requiresManualExecution(PlanView planView) {
        return planView.steps().stream().anyMatch(step ->
                TaskType.DATA_QUERY_PREPARE.name().equals(step.taskType())
                        || TaskType.DATA_QUERY_EXECUTE.name().equals(step.taskType()));
    }

    public String buildPlanningReply(PlanView planView) {
        StringBuilder builder = new StringBuilder("已识别为综合分析任务，已生成执行计划：\n");
        for (PlanStepView step : planView.steps()) {
            builder.append(step.stepOrder()).append(". ").append(step.name()).append('\n');
        }
        builder.append("可通过计划执行接口逐步推进数据查询、依据整理和结论生成。");
        return builder.toString();
    }

    private List<PlanStep> buildSteps(String planId, AgentPlanGraph graph) {
        List<PlanStep> steps = new ArrayList<>();
        int stepOrder = 1;
        for (AgentTask task : graph.tasks()) {
            steps.add(new PlanStep(
                    planId,
                    stepOrder++,
                    task.taskCode(),
                    task.taskType(),
                    task.name(),
                    task.goal(),
                    joinDependencies(task.dependencyOrders()),
                    task.mandatory()
            ));
        }
        return steps;
    }

    private boolean isComplexQuestion(ParsedQuestion parsedQuestion) {
        String normalized = normalize(parsedQuestion.originalQuestion());
        boolean multiIntent = parsedQuestion.intents().size() >= 2;
        boolean analyticalOutput = parsedQuestion.slotsOf(SlotType.OUTPUT_FORMAT).stream()
                .map(slot -> slot.normalizedValue().toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.equals("analysis")
                        || value.equals("trend")
                        || value.equals("comparison")
                        || value.equals("ranking"));
        return normalized.length() >= 12
                && (multiIntent
                || analyticalOutput
                || parsedQuestion.hasMandatoryDataIntent() && parsedQuestion.requiresCitation()
                || normalized.contains("形成"));
    }

    private boolean containsDataRequirement(ParsedQuestion parsedQuestion) {
        return parsedQuestion.hasMandatoryDataIntent();
    }

    private boolean containsKnowledgeRequirement(ParsedQuestion parsedQuestion) {
        return parsedQuestion.requiresCitation();
    }

    private String normalize(String question) {
        return question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
    }

    private String joinDependencies(List<Integer> dependencyOrders) {
        if (dependencyOrders == null || dependencyOrders.isEmpty()) {
            return null;
        }
        return dependencyOrders.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
    }

    private String truncate(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength);
    }

    private String summarizeRows(QueryExecuteView executeView) {
        if (executeView.rows().isEmpty()) {
            return "未返回明细数据";
        }
        return "首行结果：" + truncate(executeView.rows().get(0).toString(), 120);
    }

    private PlanView markStepFailed(String runId,
                                    Plan plan,
                                    List<PlanStep> steps,
                                    PlanStep step,
                                    PlanStepFailureArtifact failureArtifact,
                                    String reason) {
        step.fail(reason, null, planStepFailureArtifactMapper.write(failureArtifact));
        plan.fail();
        planStepRepository.save(step);
        auditLogService.recordPlanStep(step, runId, "plan.step.fail", null, null, reason);
        saveToolCall(
                runId,
                step.getId(),
                "plan.step_failed",
                truncate(step.getStepOrder() + ". " + step.getName(), 200),
                truncate(reason, 200),
                null,
                null
        );
        Plan savedPlan = planRepository.save(plan);
        return planViewMapper.toView(savedPlan, steps);
    }

    private String safeFailureMessage(RuntimeException ex) {
        if (ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "步骤执行异常";
        }
        return ex.getMessage();
    }

    private PlanStepFailureArtifact buildFailureArtifact(BusinessException ex) {
        return new PlanStepFailureArtifact(resolveBusinessFailureCode(ex), safeFailureMessage(ex));
    }

    private PlanStepFailureArtifact buildFailureArtifact(RuntimeException ex) {
        if (ex instanceof BusinessException businessException) {
            return buildFailureArtifact(businessException);
        }
        return new PlanStepFailureArtifact(PlanStepFailureCode.PLAN_STEP_EXECUTION_FAILED, safeFailureMessage(ex));
    }

    private PlanStepFailureCode resolveBusinessFailureCode(BusinessException ex) {
        return switch (ex.errorCode()) {
            case SQL_PERMISSION_DENIED -> PlanStepFailureCode.SQL_PERMISSION_DENIED;
            default -> PlanStepFailureCode.PLAN_STEP_EXECUTION_FAILED;
        };
    }

    private void ensureDependenciesReady(String runId, Plan plan, List<PlanStep> steps, PlanStep step) {
        String dependencyStepIds = step.getDependencyStepIds();
        if (dependencyStepIds == null || dependencyStepIds.isBlank()) {
            return;
        }
        for (String dependencyStepOrder : dependencyStepIds.split(",")) {
            int order = parseStepOrder(dependencyStepOrder);
            PlanStep dependency = findStepByOrder(steps, order)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_STEP_NOT_FOUND));
            if (dependency.getStatus() == PlanStatus.ABANDONED) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "前置步骤已废弃，无法继续执行");
            }
            if (dependency.getStatus() != PlanStatus.COMPLETED) {
                recordDependencyAutorun(runId, step, dependency);
                reExecuteDependency(runId, plan, steps, dependency);
            }
        }
    }

    private void recordDependencyAutorun(String runId, PlanStep triggerStep, PlanStep dependency) {
        saveToolCall(
                runId,
                triggerStep.getId(),
                "plan.dependency_autorun",
                truncate(stepRelationPayload(triggerStep, dependency), 200),
                truncate("前置步骤状态为 " + dependency.getStatus() + "，系统已自动补跑。", 200),
                null,
                null
        );
    }

    private void recordDependencyRecovery(String runId, PlanStep triggerStep, PlanStep dependency) {
        saveToolCall(
                runId,
                triggerStep.getId(),
                "plan.dependency_recover",
                truncate(stepRelationPayload(triggerStep, dependency), 200),
                truncate("前置步骤产物缺失，系统已自动重建。", 200),
                null,
                null
        );
    }

    private String stepRelationPayload(PlanStep triggerStep, PlanStep dependency) {
        return "triggerStepOrder=" + triggerStep.getStepOrder()
                + ";triggerStepName=" + triggerStep.getName()
                + ";dependencyStepOrder=" + dependency.getStepOrder()
                + ";dependencyStepName=" + dependency.getName();
    }

    private int parseStepOrder(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计划步骤依赖配置无效");
        }
    }

    private Optional<PlanStep> findStepByOrder(List<PlanStep> steps, int stepOrder) {
        return steps.stream()
                .filter(item -> item.getStepOrder() == stepOrder)
                .findFirst();
    }

    private AgentAnswer buildPlanAnswer(String runId, Plan plan, List<PlanStep> steps) {
        ParsedQuestion parsedQuestion = questionParsingService.loadOrAnalyze(runId, plan.getGoal());
        List<QueryCardView> queryCards = loadPlanQueryCards(steps);
        List<MessageCitationView> citations = loadPlanCitations(plan.getGoal(), parsedQuestion, steps);
        List<EvidenceRef> evidenceRefs = toEvidenceRefs(citations);
        QueryAnswerView queryAnswer = new QueryAnswerView(
                "plan",
                buildPlanConclusion(queryCards, citations),
                collectPlanWarnings(queryCards),
                queryCards.stream().map(QueryCardView::dataStatement).toList(),
                queryCards,
                List.of()
        );
        ComposedAnswer composedAnswer = answerComposer.compose(parsedQuestion, queryAnswer, evidenceRefs);
        return finalGuardrailService.validate(parsedQuestion, composedAnswer)
                .<AgentAnswer>map(message -> new AgentAnswer(message, citations, null, null, null, null))
                .orElseGet(() -> new AgentAnswer(composedAnswer.render(), citations, null, null, null, composedAnswer));
    }

    private List<QueryCardView> loadPlanQueryCards(List<PlanStep> steps) {
        return steps.stream()
                .filter(step -> step.getTaskType() == TaskType.DATA_QUERY_EXECUTE)
                .map(planExecutionContextService::extractQueryId)
                .flatMap(Optional::stream)
                .map(queryRecordRepository::findById)
                .flatMap(Optional::stream)
                .map(this::toPlanQueryCard)
                .toList();
    }

    private QueryCardView toPlanQueryCard(QueryRecord queryRecord) {
        String metricName = queryRecord.getMetricName() == null || queryRecord.getMetricName().isBlank()
                ? "只读查询结果"
                : queryRecord.getMetricName();
        String resultSummary = queryRecord.getResultSummary() == null || queryRecord.getResultSummary().isBlank()
                ? "执行已完成"
                : queryRecord.getResultSummary();
        List<Map<String, Object>> rows = loadPlanRows(queryRecord);
        int rowCount = rows.isEmpty() ? parseRowCount(resultSummary) : rows.size();
        List<String> fields = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());
        DataFragment dataFragment = new DataFragment(queryRecord.getId(), fields, rowCount, resultSummary);
        DataStatement dataStatement = new DataStatement(
                queryRecord.getId(),
                queryRecord.getMetricCode(),
                metricName,
                queryRecord.getSourceSummary(),
                queryRecord.getScopeSummary(),
                queryRecord.getDataUpdatedAt(),
                queryRecord.getPermissionRewrite(),
                queryRecord.getCaliberVersion(),
                rowCount == 0 ? "当前范围未命中数据" : "结果已按现有权限过滤"
        );
        return new QueryCardView(
                queryRecord.getId(),
                queryRecord.getQuestion(),
                queryRecord.getMetricCode(),
                metricName,
                queryRecord.getScopeSummary(),
                resultSummary,
                queryRecord.getPermissionRewrite(),
                dataFragment,
                dataStatement,
                List.of(),
                rowCount,
                queryRecord.getCreatedAt(),
                rows
        );
    }

    private List<Map<String, Object>> loadPlanRows(QueryRecord queryRecord) {
        String executedSql = queryRecord.getExecutedSqlSummary();
        if (executedSql == null || executedSql.isBlank()) {
            return List.of();
        }
        try {
            return organizationDimensionTranslator.translate(sanitizeRows(readonlySqlQueryService.execute(executedSql)));
        } catch (BusinessException ex) {
            return List.of();
        }
    }

    private int parseRowCount(String resultSummary) {
        if (resultSummary == null || resultSummary.isBlank()) {
            return 0;
        }
        Matcher matcher = ROW_COUNT_PATTERN.matcher(resultSummary);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private List<String> collectPlanWarnings(List<QueryCardView> queryCards) {
        return queryCards.stream()
                .flatMap(card -> card.warnings().stream())
                .distinct()
                .toList();
    }

    private String buildPlanConclusion(List<QueryCardView> queryCards, List<MessageCitationView> citations) {
        if (queryCards.isEmpty()) {
            return "已完成计划执行，但当前没有形成可审计的数据结果。";
        }
        if (queryCards.size() == 1) {
            QueryCardView card = queryCards.get(0);
            String metricName = card.metricName() == null || card.metricName().isBlank() ? "查询结果" : card.metricName();
            String rankingSummary = buildPlanRankingSummary(card, metricName);
            StringBuilder builder = new StringBuilder(rankingSummary.isBlank() ? metricName + "已完成核验，" + card.resultSummary() : rankingSummary);
            if (!citations.isEmpty()) {
                builder.append("，并补充").append(citations.size()).append("条依据。");
            } else {
                builder.append("。");
            }
            return builder.toString();
        }
        StringBuilder builder = new StringBuilder("已完成综合分析，共汇总")
                .append(queryCards.size())
                .append("项查询结果");
        if (!citations.isEmpty()) {
            builder.append("，并补充").append(citations.size()).append("条依据");
        }
        return builder.append("。").toString();
    }

    private String buildPlanRankingSummary(QueryCardView card, String metricName) {
        if (card == null || card.rows().size() <= 1) {
            return "";
        }
        Map<String, Object> firstRow = card.rows().get(0);
        if (firstRow.containsKey("STREET_NAME") || firstRow.containsKey("street_name")) {
            return buildTopItemsSummary(card.rows(), metricName, "STREET_NAME", "当前排行中");
        }
        if (firstRow.containsKey("UNIT_NAME") || firstRow.containsKey("unit_name")) {
            return buildTopItemsSummary(card.rows(), metricName, "UNIT_NAME", "当前排行中");
        }
        return "";
    }

    private String buildTopItemsSummary(List<Map<String, Object>> rows, String metricName, String key, String prefix) {
        List<String> items = rows.stream()
                .limit(3)
                .map(row -> {
                    Object name = row.getOrDefault(key, row.get(key.toLowerCase(Locale.ROOT)));
                    Double value = readMetricValue(row);
                    if (name == null || value == null) {
                        return null;
                    }
                    return name + " " + formatMetricValue(value, metricName);
                })
                .filter(item -> item != null && !item.isBlank())
                .toList();
        if (items.isEmpty()) {
            return "";
        }
        return prefix + "，" + String.join("，", items);
    }

    private Double readMetricValue(Map<String, Object> row) {
        Object value = row.get("METRIC_VALUE");
        if (value == null) {
            value = row.get("metric_value");
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String formatMetricValue(Double value, String metricName) {
        if (value == null) {
            return "无结果";
        }
        if (metricName != null && metricName.contains("率")) {
            return String.format(Locale.ROOT, "%.2f%%", value);
        }
        if (Math.abs(value - Math.rint(value)) < 0.0001d) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private List<Map<String, Object>> sanitizeRows(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> {
                    Map<String, Object> sanitized = new LinkedHashMap<>();
                    row.forEach((key, value) -> sanitized.put(key, sanitizeValue(value)));
                    return sanitized;
                })
                .toList();
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof Number || value instanceof String || value instanceof Boolean || value == null) {
            return value;
        }
        return value.toString();
    }

    private List<MessageCitationView> loadPlanCitations(String question, ParsedQuestion parsedQuestion, List<PlanStep> steps) {
        boolean requiresKnowledge = parsedQuestion.requiresCitation()
                || steps.stream().anyMatch(step -> step.getTaskType() == TaskType.KNOWLEDGE_RETRIEVE && step.getStatus() == PlanStatus.COMPLETED);
        if (!requiresKnowledge) {
            return List.of();
        }
        List<MessageCitationView> persistedCitations = knowledgePlanContextService.loadPersistedCitations(
                planExecutionContextService.extractKnowledgeDocumentIds(steps)
        );
        if (!persistedCitations.isEmpty()) {
            return persistedCitations;
        }
        KnowledgeCategory category = parsedQuestion.preferredKnowledgeCategory().orElse(null);
        List<KnowledgeSearchHit> hits = knowledgeSearchService.search(question, category, 4);
        if (hits.isEmpty()) {
            hits = knowledgeSearchService.search(question, null, 4);
        }
        return hits.stream()
                .map(KnowledgeSearchHit::toCitation)
                .toList();
    }

    private List<EvidenceRef> toEvidenceRefs(List<MessageCitationView> citations) {
        Map<String, EvidenceRef> refs = new LinkedHashMap<>();
        for (MessageCitationView citation : citations) {
            refs.putIfAbsent(
                    citation.documentId(),
                    EvidenceRef.fromCitation(
                            citation.documentId(),
                            citation.documentId(),
                            citation.documentTitle(),
                            citation.sectionTitle(),
                            citation.sourceOrg(),
                            citation.effectiveFrom(),
                            citation.effectiveTo()
                    )
            );
        }
        return List.copyOf(refs.values());
    }

    private void savePlanAnswerMessage(String runId, AgentAnswer answer) {
        AgentRun run = agentRunRepository.findByIdWithSessionOwner(runId, UserContextHolder.get().userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));
        agentMessageRepository.save(new AgentMessage(
                run.getSessionId(),
                MessageRole.ASSISTANT,
                answer.content(),
                writeCitations(answer.citations()),
                writeStructuredAnswer(answer.composedAnswer()),
                answer.riskLevel(),
                answer.reviewId()
        ));
    }

    public record PlanAutoExecutionResult(PlanView planView, AgentMessage answerMessage) {
    }

    private String writeCitations(List<MessageCitationView> citations) {
        if (citations.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String writeStructuredAnswer(ComposedAnswer composedAnswer) {
        if (composedAnswer == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(composedAnswer);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private ToolCall saveToolCall(String runId,
                                  String taskId,
                                  String toolName,
                                  String inputSummary,
                                  String outputSummary,
                                  String queryId,
                                  String evidenceId) {
        ToolCall toolCall = toolCallRepository.save(new ToolCall(runId, toolName, inputSummary, outputSummary));
        auditLogService.recordToolCall(toolCall, taskId, queryId, evidenceId);
        return toolCall;
    }
}
