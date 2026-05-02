package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.PlanStepArtifact;
import com.example.urbanagent.agent.application.dto.PlanStepExecutionTraceView;
import com.example.urbanagent.agent.application.dto.PlanStepFailureArtifact;
import com.example.urbanagent.agent.application.dto.PlanStepFailureCode;
import com.example.urbanagent.agent.application.dto.PlanStepFailureView;
import com.example.urbanagent.agent.application.dto.PlanStepSystemActionView;
import com.example.urbanagent.agent.application.dto.PlanSystemSummaryView;
import com.example.urbanagent.agent.application.dto.PlanStepView;
import com.example.urbanagent.agent.application.dto.PlanView;
import com.example.urbanagent.agent.application.dto.TaskType;
import com.example.urbanagent.agent.domain.Plan;
import com.example.urbanagent.agent.domain.PlanStep;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class PlanViewMapper {

    private final PlanStepArtifactMapper artifactMapper;
    private final PlanStepFailureArtifactMapper failureArtifactMapper;
    private final PlanStepFailureTemplateResolver failureTemplateResolver;
    private final PlanRetryAdvisor planRetryAdvisor;
    private final PlanStepSystemActionService planStepSystemActionService;

    public PlanViewMapper(PlanStepArtifactMapper artifactMapper,
                          PlanStepFailureArtifactMapper failureArtifactMapper,
                          PlanStepFailureTemplateResolver failureTemplateResolver,
                          PlanRetryAdvisor planRetryAdvisor,
                          PlanStepSystemActionService planStepSystemActionService) {
        this.artifactMapper = artifactMapper;
        this.failureArtifactMapper = failureArtifactMapper;
        this.failureTemplateResolver = failureTemplateResolver;
        this.planRetryAdvisor = planRetryAdvisor;
        this.planStepSystemActionService = planStepSystemActionService;
    }

    public PlanView toView(Plan plan, List<PlanStep> steps) {
        Map<Integer, List<PlanStepSystemActionView>> systemActionsByStep =
                planStepSystemActionService.loadByRunId(plan.getRunId());
        return new PlanView(
                plan.getId(),
                plan.getRunId(),
                plan.getGoal(),
                plan.getStatus().name(),
                plan.getConfirmStatus().name(),
                buildSystemSummary(systemActionsByStep),
                steps.stream().map(step -> toView(step, steps, systemActionsByStep)).toList(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }

    private PlanStepView toView(PlanStep step,
                                List<PlanStep> steps,
                                Map<Integer, List<PlanStepSystemActionView>> systemActionsByStep) {
        var retryAdvice = planRetryAdvisor.advise(step, steps);
        return new PlanStepView(
                step.getId(),
                step.getStepOrder(),
                step.getTaskCode(),
                step.getTaskType().name(),
                step.getName(),
                step.getGoal(),
                step.getStatus().name(),
                step.getDependencyStepIds(),
                step.isMandatory(),
                step.getOutputSummary(),
                step.getResultRef(),
                buildExecutionTrace(step, systemActionsByStep.getOrDefault(step.getStepOrder(), List.of())),
                buildFailureDetail(step, steps, retryAdvice),
                retryAdvice,
                systemActionsByStep.getOrDefault(step.getStepOrder(), List.of()),
                parseOutputPayload(step.getOutputPayloadJson()),
                step.getCreatedAt(),
                step.getUpdatedAt()
        );
    }

    private PlanSystemSummaryView buildSystemSummary(Map<Integer, List<PlanStepSystemActionView>> systemActionsByStep) {
        List<PlanStepSystemActionView> actions = systemActionsByStep.values().stream()
                .flatMap(List::stream)
                .toList();
        int autorunCount = (int) actions.stream()
                .filter(action -> "AUTORUN".equals(action.action()))
                .count();
        int recoverCount = (int) actions.stream()
                .filter(action -> "RECOVER".equals(action.action()))
                .count();
        int affectedStepCount = systemActionsByStep.size();
        Instant lastActionAt = actions.stream()
                .map(PlanStepSystemActionView::createdAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        String summary = buildSystemSummaryText(autorunCount, recoverCount, affectedStepCount);
        return new PlanSystemSummaryView(autorunCount, recoverCount, affectedStepCount, lastActionAt, summary);
    }

    private PlanStepExecutionTraceView buildExecutionTrace(PlanStep step, List<PlanStepSystemActionView> systemActions) {
        if ((systemActions == null || systemActions.isEmpty())
                && step.getResultRef() == null
                && !isExecutedStatus(step.getStatus().name())) {
            return null;
        }

        String triggerMode = "USER";
        String triggerLabel = "用户执行";
        int systemActionCount = systemActions == null ? 0 : systemActions.size();
        Instant lastActionAt = systemActions == null ? null : systemActions.stream()
                .map(PlanStepSystemActionView::createdAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        if (systemActionCount > 0) {
            boolean hasRecover = systemActions.stream().anyMatch(action -> "RECOVER".equals(action.action()));
            if (hasRecover) {
                triggerMode = "SYSTEM_RECOVER";
                triggerLabel = "系统重建";
            } else {
                triggerMode = "SYSTEM_AUTORUN";
                triggerLabel = "系统补跑";
            }
        }
        return new PlanStepExecutionTraceView(
                triggerMode,
                triggerLabel,
                systemActionCount,
                lastActionAt,
                step.getResultRef(),
                describeResultRef(step.getResultRef())
        );
    }

    private PlanStepFailureView buildFailureDetail(PlanStep step,
                                                   List<PlanStep> allSteps,
                                                   com.example.urbanagent.agent.application.dto.PlanStepRetryView retryAdvice) {
        if (!"FAILED".equals(step.getStatus().name())) {
            return null;
        }
        PlanStepFailureArtifact failureArtifact = failureArtifactMapper.read(step).orElse(null);
        PlanStepFailureCode errorCode = resolveFailureCode(step, allSteps, retryAdvice, failureArtifact);
        PlanStepFailureTemplateResolver.PlanStepFailureTemplate template = failureTemplateResolver.resolve(errorCode);
        String reason = template.dependencyBlocked()
                ? fallbackFailureReason(retryAdvice == null ? null : retryAdvice.reason(),
                messageFromArtifact(failureArtifact),
                step.getOutputSummary(),
                template.defaultReason())
                : fallbackFailureReason(messageFromArtifact(failureArtifact),
                step.getOutputSummary(),
                retryAdvice == null ? null : retryAdvice.reason(),
                template.defaultReason());
        return new PlanStepFailureView(
                errorCode,
                template.category(),
                template.headline(),
                reason,
                template.action(),
                template.actionLabel(),
                template.handleCode(),
                template.dependencyBlocked(),
                retryAdvice == null ? List.of() : retryAdvice.prerequisiteStepOrders()
        );
    }

    private boolean isExecutedStatus(String status) {
        return "COMPLETED".equals(status)
                || "SUCCESS".equals(status)
                || "RUNNING".equals(status)
                || "IN_PROGRESS".equals(status)
                || "FAILED".equals(status)
                || "CANCELLED".equals(status)
                || "ABANDONED".equals(status);
    }

    private String fallbackFailureReason(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallbackFailureReason(fallback);
    }

    private String fallbackFailureReason(String firstFallback, String secondFallback, String defaultFallback) {
        if (firstFallback != null && !firstFallback.isBlank()) {
            return firstFallback;
        }
        return fallbackFailureReason(secondFallback, defaultFallback);
    }

    private String fallbackFailureReason(String firstFallback,
                                         String secondFallback,
                                         String thirdFallback,
                                         String defaultFallback) {
        if (firstFallback != null && !firstFallback.isBlank()) {
            return firstFallback;
        }
        return fallbackFailureReason(secondFallback, thirdFallback, defaultFallback);
    }

    private String fallbackFailureReason(String value) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return "当前步骤执行失败，请根据建议处理后重试。";
    }

    private String messageFromArtifact(PlanStepFailureArtifact artifact) {
        return artifact == null ? null : artifact.errorMessage();
    }

    private PlanStepFailureCode resolveFailureCode(PlanStep step,
                                                   List<PlanStep> allSteps,
                                                   com.example.urbanagent.agent.application.dto.PlanStepRetryView retryAdvice,
                                                   PlanStepFailureArtifact failureArtifact) {
        if (failureArtifact != null && failureArtifact.errorCode() != null) {
            return failureArtifact.errorCode();
        }
        if (retryAdvice != null && "REBUILD_DEPENDENCIES".equals(retryAdvice.action())) {
            return classifyDependencyFailureCode(step, allSteps, retryAdvice);
        }
        return PlanStepFailureCode.PLAN_STEP_EXECUTION_FAILED;
    }

    private PlanStepFailureCode classifyDependencyFailureCode(PlanStep step,
                                                              List<PlanStep> allSteps,
                                                              com.example.urbanagent.agent.application.dto.PlanStepRetryView retryAdvice) {
        List<PlanStep> dependencySteps = allSteps.stream()
                .filter(candidate -> retryAdvice.prerequisiteStepOrders().contains(candidate.getStepOrder()))
                .toList();
        if (dependencySteps.stream().anyMatch(candidate -> !"COMPLETED".equals(candidate.getStatus().name()))) {
            return PlanStepFailureCode.PLAN_DEPENDENCY_INCOMPLETE;
        }

        if (step.getTaskType() == TaskType.DATA_QUERY_EXECUTE
                && dependencySteps.stream().anyMatch(candidate -> candidate.getTaskType() == TaskType.DATA_QUERY_PREPARE)) {
            return PlanStepFailureCode.PLAN_QUERY_PREVIEW_MISSING;
        }

        if (step.getTaskType() == TaskType.ANSWER_COMPOSE) {
            boolean missingQueryResult = dependencySteps.stream()
                    .filter(candidate -> candidate.getTaskType() == TaskType.DATA_QUERY_EXECUTE)
                    .anyMatch(candidate -> artifactMapper.read(candidate)
                            .map(artifact -> artifact.queryId() == null || artifact.queryId().isBlank())
                            .orElse(true));
            if (missingQueryResult) {
                return PlanStepFailureCode.PLAN_QUERY_RESULT_MISSING;
            }

            boolean missingKnowledge = dependencySteps.stream()
                    .filter(candidate -> candidate.getTaskType() == TaskType.KNOWLEDGE_RETRIEVE)
                    .anyMatch(candidate -> artifactMapper.read(candidate)
                            .map(artifact -> artifact.documentIds() == null || artifact.documentIds().isEmpty())
                            .orElse(true));
            if (missingKnowledge) {
                return PlanStepFailureCode.PLAN_KNOWLEDGE_RESULT_MISSING;
            }
        }

        return PlanStepFailureCode.PLAN_DEPENDENCY_BLOCKED;
    }

    private String describeResultRef(String resultRef) {
        if (resultRef == null || resultRef.isBlank()) {
            return null;
        }
        if (resultRef.startsWith("query-preview:")) {
            return "查询预览";
        }
        if (resultRef.startsWith("query:")) {
            return "查询结果";
        }
        if (resultRef.startsWith("knowledge:")) {
            return "知识依据";
        }
        if (resultRef.startsWith("citations:")) {
            return "引用依据";
        }
        if ("parsed-question".equals(resultRef)) {
            return "结构化解析";
        }
        if ("preview-sql".equals(resultRef)) {
            return "候选 SQL";
        }
        if (resultRef.startsWith("row-count:")) {
            return "结果行数";
        }
        if ("answer".equals(resultRef)) {
            return "答案汇总";
        }
        if ("answer-blocked".equals(resultRef)) {
            return "答案拦截";
        }
        return "步骤结果";
    }

    private String buildSystemSummaryText(int autorunCount, int recoverCount, int affectedStepCount) {
        if (autorunCount == 0 && recoverCount == 0) {
            return "本次计划执行未触发系统自动补跑或自动重建。";
        }
        StringBuilder builder = new StringBuilder("本次计划执行");
        boolean appended = false;
        if (autorunCount > 0) {
            builder.append("自动补跑 ").append(autorunCount).append(" 次");
            appended = true;
        }
        if (recoverCount > 0) {
            if (appended) {
                builder.append("，");
            }
            builder.append("自动重建 ").append(recoverCount).append(" 次");
            appended = true;
        }
        if (affectedStepCount > 0) {
            builder.append("，涉及 ").append(affectedStepCount).append(" 个步骤。");
        } else if (appended) {
            builder.append("。");
        }
        return builder.toString();
    }

    private PlanStepArtifact parseOutputPayload(String outputPayloadJson) {
        return artifactMapper.read(outputPayloadJson).orElse(null);
    }
}
