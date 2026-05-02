package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.PlanStepRetryView;
import com.example.urbanagent.agent.application.dto.TaskType;
import com.example.urbanagent.agent.domain.PlanStatus;
import com.example.urbanagent.agent.domain.PlanStep;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class PlanRetryAdvisor {

    private final PlanExecutionContextService planExecutionContextService;

    public PlanRetryAdvisor(PlanExecutionContextService planExecutionContextService) {
        this.planExecutionContextService = planExecutionContextService;
    }

    public PlanStepRetryView advise(PlanStep step, List<PlanStep> allSteps) {
        if (step.getStatus() != PlanStatus.FAILED) {
            return null;
        }
        List<PlanStep> dependencies = dependencySteps(step, allSteps);
        List<Integer> incompleteDependencies = dependencies.stream()
                .filter(dependency -> dependency.getStatus() != PlanStatus.COMPLETED)
                .map(PlanStep::getStepOrder)
                .toList();
        if (!incompleteDependencies.isEmpty()) {
            return new PlanStepRetryView(
                    "REBUILD_DEPENDENCIES",
                    "前置步骤尚未恢复完成，请先重跑依赖步骤后再继续当前步骤。",
                    incompleteDependencies
            );
        }
        return switch (step.getTaskType()) {
            case DATA_QUERY_EXECUTE -> adviseDataQueryExecute(requiredDependencyRebuildOrders(step, allSteps));
            case ANSWER_COMPOSE -> adviseAnswerCompose(dependencies);
            default -> new PlanStepRetryView(
                    "RETRY_CURRENT",
                    "当前步骤没有缺失的前序产物，可以直接重试。",
                    List.of()
            );
        };
    }

    public List<Integer> requiredDependencyRebuildOrders(PlanStep step, List<PlanStep> allSteps) {
        List<PlanStep> dependencies = dependencySteps(step, allSteps);
        return switch (step.getTaskType()) {
            case DATA_QUERY_EXECUTE -> dependencies.stream()
                .filter(dependency -> dependency.getTaskType() == TaskType.DATA_QUERY_PREPARE)
                .filter(dependency -> planExecutionContextService.read(dependency)
                        .map(artifact -> artifact.validatedSql() == null || artifact.validatedSql().isBlank())
                        .orElse(true))
                .map(PlanStep::getStepOrder)
                .toList();
            case ANSWER_COMPOSE -> {
                List<Integer> missingQuerySteps = dependencies.stream()
                        .filter(dependency -> dependency.getTaskType() == TaskType.DATA_QUERY_EXECUTE)
                        .filter(dependency -> planExecutionContextService.extractQueryId(dependency).isEmpty())
                        .map(PlanStep::getStepOrder)
                        .toList();
                List<Integer> invalidKnowledgeSteps = dependencies.stream()
                        .filter(dependency -> dependency.getTaskType() == TaskType.KNOWLEDGE_RETRIEVE)
                        .filter(dependency -> planExecutionContextService.read(dependency).isEmpty())
                        .map(PlanStep::getStepOrder)
                        .toList();
                yield java.util.stream.Stream.concat(missingQuerySteps.stream(), invalidKnowledgeSteps.stream())
                        .distinct()
                        .toList();
            }
            default -> List.of();
        };
    }

    private PlanStepRetryView adviseDataQueryExecute(List<Integer> missingPrepareSteps) {
        if (!missingPrepareSteps.isEmpty()) {
            return new PlanStepRetryView(
                    "REBUILD_DEPENDENCIES",
                    "查询准备产物已缺失，请先重跑数据查询准备步骤。",
                    missingPrepareSteps
            );
        }
        return new PlanStepRetryView(
                "RETRY_CURRENT",
                "查询准备结果仍可复用，可以直接重试当前查询执行步骤。",
                List.of()
        );
    }

    private PlanStepRetryView adviseAnswerCompose(List<PlanStep> dependencies) {
        List<Integer> missingQuerySteps = dependencies.stream()
                .filter(dependency -> dependency.getTaskType() == TaskType.DATA_QUERY_EXECUTE)
                .filter(dependency -> planExecutionContextService.extractQueryId(dependency).isEmpty())
                .map(PlanStep::getStepOrder)
                .toList();
        if (!missingQuerySteps.isEmpty()) {
            return new PlanStepRetryView(
                    "REBUILD_DEPENDENCIES",
                    "答案生成依赖的数据结果已失效，请先重跑数据查询执行步骤。",
                    missingQuerySteps
            );
        }
        List<Integer> invalidKnowledgeSteps = dependencies.stream()
                .filter(dependency -> dependency.getTaskType() == TaskType.KNOWLEDGE_RETRIEVE)
                .filter(dependency -> planExecutionContextService.read(dependency).isEmpty())
                .map(PlanStep::getStepOrder)
                .toList();
        if (!invalidKnowledgeSteps.isEmpty()) {
            return new PlanStepRetryView(
                    "REBUILD_DEPENDENCIES",
                    "依据检索产物已缺失，请先重跑依据检索步骤。",
                    invalidKnowledgeSteps
            );
        }
        return new PlanStepRetryView(
                "RETRY_CURRENT",
                "数据结果和依据产物仍可复用，可以直接重试答案生成步骤。",
                List.of()
        );
    }

    private List<PlanStep> dependencySteps(PlanStep step, List<PlanStep> allSteps) {
        return dependencyOrders(step).stream()
                .map(order -> findStepByOrder(allSteps, order))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Integer> dependencyOrders(PlanStep step) {
        String dependencyStepIds = step.getDependencyStepIds();
        if (dependencyStepIds == null || dependencyStepIds.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(dependencyStepIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Integer::parseInt)
                .toList();
    }

    private Optional<PlanStep> findStepByOrder(List<PlanStep> allSteps, Integer stepOrder) {
        return allSteps.stream()
                .filter(candidate -> candidate.getStepOrder().equals(stepOrder))
                .findFirst();
    }
}
