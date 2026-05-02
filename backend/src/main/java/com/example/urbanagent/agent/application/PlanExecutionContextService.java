package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.PlanStepArtifact;
import com.example.urbanagent.agent.application.dto.TaskType;
import com.example.urbanagent.agent.domain.PlanStatus;
import com.example.urbanagent.agent.domain.PlanStep;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PlanExecutionContextService {

    private final PlanStepArtifactMapper artifactMapper;

    public PlanExecutionContextService(PlanStepArtifactMapper artifactMapper) {
        this.artifactMapper = artifactMapper;
    }

    public String write(PlanStepArtifact artifact) {
        return artifactMapper.write(artifact);
    }

    public Optional<PlanStepArtifact> read(PlanStep step) {
        return artifactMapper.read(step);
    }

    public String requirePreparedSql(List<PlanStep> steps) {
        return steps.stream()
                .filter(step -> step.getTaskType() == TaskType.DATA_QUERY_PREPARE)
                .filter(step -> step.getStatus() == PlanStatus.COMPLETED)
                .map(this::read)
                .flatMap(Optional::stream)
                .map(PlanStepArtifact::validatedSql)
                .filter(sql -> sql != null && !sql.isBlank())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "请先执行数据口径梳理与查询准备步骤"));
    }

    public Optional<String> extractQueryId(PlanStep step) {
        Optional<PlanStepArtifact> artifact = read(step);
        if (artifact.isPresent() && artifact.get().queryId() != null && !artifact.get().queryId().isBlank()) {
            return Optional.of(artifact.get().queryId());
        }
        String resultRef = step.getResultRef();
        if (resultRef == null || !resultRef.startsWith("query:")) {
            return Optional.empty();
        }
        String queryId = resultRef.substring("query:".length()).trim();
        if (queryId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(queryId);
    }

    public List<String> extractKnowledgeDocumentIds(List<PlanStep> steps) {
        return steps.stream()
                .filter(step -> step.getTaskType() == TaskType.KNOWLEDGE_RETRIEVE)
                .filter(step -> step.getStatus() == PlanStatus.COMPLETED)
                .map(this::read)
                .flatMap(Optional::stream)
                .flatMap(artifact -> artifact.documentIds().stream())
                .filter(documentId -> documentId != null && !documentId.isBlank())
                .distinct()
                .toList();
    }
}
