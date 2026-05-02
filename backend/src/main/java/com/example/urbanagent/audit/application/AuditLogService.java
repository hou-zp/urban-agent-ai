package com.example.urbanagent.audit.application;

import com.example.urbanagent.agent.domain.AgentRun;
import com.example.urbanagent.agent.domain.AgentSession;
import com.example.urbanagent.agent.domain.PlanStep;
import com.example.urbanagent.agent.domain.ToolCall;
import com.example.urbanagent.audit.application.dto.AuditLogQuery;
import com.example.urbanagent.audit.application.dto.AuditLogView;
import com.example.urbanagent.audit.domain.AuditLog;
import com.example.urbanagent.audit.repository.AuditLogRepository;
import com.example.urbanagent.knowledge.domain.KnowledgeDocument;
import com.example.urbanagent.query.domain.QueryRecord;
import com.example.urbanagent.risk.domain.RiskEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void recordAgentSessionCreated(AgentSession session) {
        persist(new AuditLog(
                session.getUserId(),
                "agent.session.create",
                "agent_session",
                session.getId(),
                session.getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                session.getStatus().name(),
                0L,
                writeDetail(Map.of(
                        "title", session.getTitle()
                ))
        ));
    }

    public void recordAgentRunStarted(AgentRun run) {
        persist(new AuditLog(
                run.getUserId(),
                "agent.run.start",
                "agent_run",
                run.getId(),
                run.getSessionId(),
                run.getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                run.getStatus().name(),
                0L,
                writeDetail(Map.of(
                        "question", run.getQuestion(),
                        "modelName", run.getModelName()
                ))
        ));
    }

    public void recordAgentRunCompleted(AgentRun run, String reason) {
        recordAgentRunStatus("agent.run.complete", run, reason);
    }

    public void recordAgentRunPendingReview(AgentRun run, String reviewId) {
        recordAgentRunStatus("agent.run.pending_review", run, reviewId == null ? null : "reviewId=" + reviewId);
    }

    public void recordAgentRunCancelled(AgentRun run, String reason) {
        recordAgentRunStatus("agent.run.cancel", run, reason);
    }

    public void recordAgentRunFailed(AgentRun run, String reason) {
        recordAgentRunStatus("agent.run.fail", run, reason);
    }

    public void recordQueryAccess(QueryRecord queryRecord, Long durationMs) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("question", queryRecord.getQuestion());
        detail.put("metricCode", queryRecord.getMetricCode());
        detail.put("metricName", queryRecord.getMetricName());
        detail.put("candidateSql", queryRecord.getCandidateSql());
        detail.put("executedSqlSummary", queryRecord.getExecutedSqlSummary());
        detail.put("permissionRewrite", queryRecord.getPermissionRewrite());
        detail.put("resultSummary", queryRecord.getResultSummary());
        detail.put("scopeSummary", queryRecord.getScopeSummary());
        detail.put("sourceSummary", queryRecord.getSourceSummary());
        detail.put("caliberVersion", queryRecord.getCaliberVersion());
        persist(new AuditLog(
                queryRecord.getUserId(),
                queryRecord.getStatus().name().equals("PREVIEWED") ? "query.preview" : "query.execute",
                "query_record",
                queryRecord.getId(),
                null,
                null,
                null,
                null,
                queryRecord.getId(),
                null,
                null,
                summarizeSql(queryRecord),
                null,
                queryRecord.getStatus().name(),
                durationMs,
                writeDetail(detail)
        ));
    }

    public void recordKnowledgeDocumentUploaded(KnowledgeDocument document) {
        persist(new AuditLog(
                document.getCreateUserId(),
                "knowledge.upload",
                "knowledge_document",
                document.getId(),
                null,
                null,
                null,
                null,
                null,
                document.getId(),
                null,
                null,
                null,
                document.getStatus().name(),
                0L,
                writeDetail(buildKnowledgeDetail(document))
        ));
    }

    public void recordKnowledgeDocumentIndexed(KnowledgeDocument document, Long durationMs) {
        persist(new AuditLog(
                document.getCreateUserId(),
                "knowledge.index",
                "knowledge_document",
                document.getId(),
                null,
                null,
                null,
                null,
                null,
                document.getId(),
                null,
                null,
                null,
                document.getStatus().name(),
                durationMs,
                writeDetail(buildKnowledgeDetail(document))
        ));
    }

    public void recordKnowledgeDocumentStatusUpdated(KnowledgeDocument document) {
        persist(new AuditLog(
                document.getCreateUserId(),
                "knowledge.status_update",
                "knowledge_document",
                document.getId(),
                null,
                null,
                null,
                null,
                null,
                document.getId(),
                null,
                null,
                null,
                document.getStatus().name(),
                0L,
                writeDetail(buildKnowledgeDetail(document))
        ));
    }

    public void recordKnowledgeAttachmentUploaded(KnowledgeDocument document) {
        persist(new AuditLog(
                resolveAuditUserId(),
                "knowledge.attachment.upload",
                "knowledge_attachment",
                document.getId(),
                null,
                null,
                null,
                null,
                null,
                document.getId(),
                null,
                null,
                null,
                document.getStatus().name(),
                0L,
                writeDetail(buildKnowledgeAttachmentDetail(document))
        ));
    }

    public void recordKnowledgeAttachmentDownloaded(KnowledgeDocument document) {
        persist(new AuditLog(
                resolveAuditUserId(),
                "knowledge.attachment.download",
                "knowledge_attachment",
                document.getId(),
                null,
                null,
                null,
                null,
                null,
                document.getId(),
                null,
                null,
                null,
                document.getStatus().name(),
                0L,
                writeDetail(buildKnowledgeAttachmentDetail(document))
        ));
    }

    public void recordPlanStep(PlanStep step, String runId, String actionType, String queryId, String evidenceId, String detailSummary) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("stepOrder", step.getStepOrder());
        detail.put("stepName", step.getName());
        detail.put("taskCode", step.getTaskCode());
        detail.put("taskType", step.getTaskType().name());
        detail.put("resultRef", step.getResultRef());
        detail.put("summary", detailSummary);
        persist(new AuditLog(
                resolveAuditUserId(),
                actionType,
                "plan_step",
                step.getId(),
                null,
                runId,
                step.getId(),
                null,
                queryId,
                evidenceId,
                null,
                null,
                null,
                step.getStatus().name(),
                0L,
                writeDetail(detail)
        ));
    }

    public void recordToolCall(ToolCall toolCall, String taskId, String queryId, String evidenceId) {
        persist(new AuditLog(
                resolveAuditUserId(),
                "tool.call",
                "tool_call",
                toolCall.getId(),
                null,
                toolCall.getRunId(),
                taskId,
                toolCall.getId(),
                queryId,
                evidenceId,
                null,
                null,
                null,
                "SUCCESS",
                0L,
                writeDetail(Map.of(
                        "toolName", toolCall.getToolName(),
                        "inputSummary", toolCall.getInputSummary(),
                        "outputSummary", toolCall.getOutputSummary()
                ))
        ));
    }

    public void recordRiskEvent(String actionType, RiskEvent riskEvent) {
        persist(new AuditLog(
                riskEvent.getUserId(),
                actionType,
                "risk_event",
                riskEvent.getId(),
                riskEvent.getSessionId(),
                riskEvent.getRunId(),
                null,
                null,
                null,
                null,
                riskEvent.getId(),
                null,
                riskEvent.getRiskLevel().name(),
                riskEvent.isReviewRequired() ? "REVIEW_REQUIRED" : "BLOCKED",
                0L,
                writeDetail(Map.of(
                        "question", riskEvent.getQuestion(),
                        "riskCategories", riskEvent.getRiskCategories(),
                        "triggerReason", riskEvent.getTriggerReason(),
                        "reviewRequired", riskEvent.isReviewRequired()
                ))
        ));
    }

    public List<AuditLogView> search(AuditLogQuery query) {
        Specification<AuditLog> specification = (root, ignoredQuery, builder) -> builder.conjunction();
        if (hasText(query.runId())) {
            specification = specification.and((root, ignoredQuery, builder) -> builder.equal(root.get("runId"), query.runId()));
        }
        if (hasText(query.taskId())) {
            specification = specification.and((root, ignoredQuery, builder) -> builder.equal(root.get("taskId"), query.taskId()));
        }
        if (hasText(query.toolCallId())) {
            specification = specification.and((root, ignoredQuery, builder) -> builder.equal(root.get("toolCallId"), query.toolCallId()));
        }
        if (hasText(query.queryId())) {
            specification = specification.and((root, ignoredQuery, builder) -> builder.equal(root.get("queryId"), query.queryId()));
        }
        if (hasText(query.evidenceId())) {
            specification = specification.and((root, ignoredQuery, builder) -> builder.equal(root.get("evidenceId"), query.evidenceId()));
        }
        return auditLogRepository.findAll(
                        specification,
                        PageRequest.of(0, query.limit(), Sort.by(Sort.Direction.DESC, "createdAt"))
                )
                .stream()
                .map(AuditLogView::from)
                .toList();
    }

    private void recordAgentRunStatus(String actionType, AgentRun run, String reason) {
        persist(new AuditLog(
                run.getUserId(),
                actionType,
                "agent_run",
                run.getId(),
                run.getSessionId(),
                run.getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                run.getStatus().name(),
                calculateDuration(run.getCreatedAt(), run.getCompletedAt()),
                writeDetail(Map.of(
                        "question", run.getQuestion(),
                        "modelName", run.getModelName(),
                        "reason", reason == null ? "" : reason
                ))
        ));
    }

    private Map<String, Object> buildKnowledgeDetail(KnowledgeDocument document) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("title", document.getTitle());
        detail.put("category", document.getCategory().name());
        detail.put("documentNumber", document.getDocumentNumber());
        detail.put("regionCode", document.getRegionCode());
        detail.put("securityLevel", document.getSecurityLevel().name());
        detail.put("failedReason", document.getFailedReason());
        return detail;
    }

    private Map<String, Object> buildKnowledgeAttachmentDetail(KnowledgeDocument document) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("title", document.getTitle());
        detail.put("attachmentRef", document.getAttachmentRef());
        detail.put("attachmentFileName", document.getAttachmentFileName());
        detail.put("attachmentMimeType", document.getAttachmentMimeType());
        detail.put("attachmentSizeBytes", document.getAttachmentSizeBytes());
        detail.put("securityLevel", document.getSecurityLevel().name());
        detail.put("regionCode", document.getRegionCode());
        return detail;
    }

    private String summarizeSql(QueryRecord queryRecord) {
        if (queryRecord.getExecutedSqlSummary() != null && !queryRecord.getExecutedSqlSummary().isBlank()) {
            return queryRecord.getExecutedSqlSummary();
        }
        return queryRecord.getCandidateSql();
    }

    private Long calculateDuration(Instant startedAt, Instant completedAt) {
        Instant effectiveCompletedAt = completedAt == null ? Instant.now() : completedAt;
        return Math.max(0L, Duration.between(startedAt, effectiveCompletedAt).toMillis());
    }

    private String writeDetail(Map<String, ?> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            return "{\"serializationError\":true}";
        }
    }

    private void persist(AuditLog auditLog) {
        auditLogRepository.save(auditLog);
    }

    private String resolveAuditUserId() {
        return Objects.requireNonNullElse(com.example.urbanagent.iam.domain.UserContextHolder.currentOrNull() == null
                ? null
                : com.example.urbanagent.iam.domain.UserContextHolder.currentOrNull().userId(), "system");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
