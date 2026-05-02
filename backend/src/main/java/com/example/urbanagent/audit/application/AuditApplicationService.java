package com.example.urbanagent.audit.application;

import com.example.urbanagent.audit.application.dto.AgentRunAuditView;
import com.example.urbanagent.audit.application.dto.AuditLogQuery;
import com.example.urbanagent.audit.application.dto.AuditLogView;
import com.example.urbanagent.audit.application.dto.ModelCallAuditView;
import com.example.urbanagent.audit.application.dto.QueryRecordAuditView;
import com.example.urbanagent.audit.application.dto.RiskEventAuditView;
import com.example.urbanagent.audit.application.dto.ToolCallAuditView;
import com.example.urbanagent.audit.domain.AuditLog;
import com.example.urbanagent.audit.repository.AuditLogRepository;
import com.example.urbanagent.ai.repository.ModelCallRecordRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AuditApplicationService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final ModelCallRecordRepository modelCallRecordRepository;
    private final ObjectMapper objectMapper;

    public AuditApplicationService(AuditLogRepository auditLogRepository,
                                   AuditLogService auditLogService,
                                   ModelCallRecordRepository modelCallRecordRepository,
                                   ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.auditLogService = auditLogService;
        this.modelCallRecordRepository = modelCallRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AgentRunAuditView> listRuns() {
        List<AuditLog> logs = findRecentLogs(
                (root, query, builder) -> builder.equal(root.get("resourceType"), "agent_run"),
                200
        );
        Map<String, AgentRunAggregate> aggregates = new LinkedHashMap<>();
        for (AuditLog log : logs) {
            if (log.getRunId() == null || log.getRunId().isBlank()) {
                continue;
            }
            Map<String, Object> detail = readDetail(log.getDetailJson());
            AgentRunAggregate aggregate = aggregates.computeIfAbsent(log.getRunId(), ignored -> new AgentRunAggregate(log.getRunId()));
            aggregate.accept(log, detail);
        }
        return aggregates.values().stream()
                .sorted((left, right) -> right.latestAt.compareTo(left.latestAt))
                .limit(20)
                .map(AgentRunAggregate::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ToolCallAuditView> listToolCalls() {
        return findRecentLogs(
                        (root, query, builder) -> builder.equal(root.get("resourceType"), "tool_call"),
                        20
                )
                .stream()
                .map(log -> {
                    Map<String, Object> detail = readDetail(log.getDetailJson());
                    return new ToolCallAuditView(
                            log.getToolCallId() == null ? log.getResourceId() : log.getToolCallId(),
                            log.getRunId(),
                            stringValue(detail.get("toolName")),
                            stringValue(detail.get("inputSummary")),
                            stringValue(detail.get("outputSummary")),
                            log.getCreatedAt()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QueryRecordAuditView> listDataAccess() {
        return findRecentLogs(
                        (root, query, builder) -> builder.equal(root.get("resourceType"), "query_record"),
                        20
                )
                .stream()
                .map(log -> {
                    Map<String, Object> detail = readDetail(log.getDetailJson());
                    return new QueryRecordAuditView(
                            log.getQueryId() == null ? log.getResourceId() : log.getQueryId(),
                            log.getUserId(),
                            stringValue(detail.get("question")),
                            stringValue(detail.get("candidateSql")),
                            stringValue(detail.get("executedSqlSummary")),
                            stringValue(detail.get("permissionRewrite")),
                            stringValue(detail.get("resultSummary")),
                            log.getStatus(),
                            log.getCreatedAt()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RiskEventAuditView> listRiskEvents() {
        return findRecentLogs(
                        (root, query, builder) -> builder.equal(root.get("resourceType"), "risk_event"),
                        20
                )
                .stream()
                .map(log -> {
                    Map<String, Object> detail = readDetail(log.getDetailJson());
                    return new RiskEventAuditView(
                            log.getRiskEventId() == null ? log.getResourceId() : log.getRiskEventId(),
                            log.getRunId(),
                            log.getSessionId(),
                            log.getUserId(),
                            stringValue(detail.get("question")),
                            log.getRiskLevel(),
                            stringValue(detail.get("riskCategories")),
                            stringValue(detail.get("triggerReason")),
                            Boolean.parseBoolean(String.valueOf(detail.getOrDefault("reviewRequired", false))),
                            log.getCreatedAt()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ModelCallAuditView> listModelCalls() {
        return modelCallRecordRepository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(ModelCallAuditView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogView> searchLogs(String runId,
                                         String taskId,
                                         String toolCallId,
                                         String queryId,
                                         String evidenceId,
                                         Integer limit) {
        return auditLogService.search(new AuditLogQuery(
                runId,
                taskId,
                toolCallId,
                queryId,
                evidenceId,
                limit == null ? 20 : limit
        ));
    }

    private List<AuditLog> findRecentLogs(Specification<AuditLog> specification, int limit) {
        return auditLogRepository.findAll(
                        specification,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
                )
                .toList();
    }

    private Map<String, Object> readDetail(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(detailJson, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of("raw", detailJson);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static final class AgentRunAggregate {
        private final String runId;
        private String sessionId;
        private String userId;
        private String question;
        private String status;
        private String modelName;
        private Instant createdAt;
        private Instant completedAt;
        private Instant latestAt;

        private AgentRunAggregate(String runId) {
            this.runId = runId;
        }

        private void accept(AuditLog log, Map<String, Object> detail) {
            if (latestAt == null || log.getCreatedAt().isAfter(latestAt)) {
                latestAt = log.getCreatedAt();
                sessionId = log.getSessionId();
                userId = log.getUserId();
                status = log.getStatus();
                if (!"agent.run.start".equals(log.getActionType())) {
                    completedAt = log.getCreatedAt();
                }
            }
            if ("agent.run.start".equals(log.getActionType())) {
                createdAt = log.getCreatedAt();
            }
            if (question == null) {
                question = stringValue(detail.get("question"));
            }
            if (modelName == null) {
                modelName = stringValue(detail.get("modelName"));
            }
        }

        private AgentRunAuditView toView() {
            return new AgentRunAuditView(
                    runId,
                    sessionId,
                    userId,
                    question,
                    status,
                    modelName,
                    createdAt == null ? latestAt : createdAt,
                    completedAt
            );
        }

        private String stringValue(Object value) {
            return value == null ? null : String.valueOf(value);
        }
    }
}
