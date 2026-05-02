package com.example.urbanagent.audit.controller;

import com.example.urbanagent.audit.application.AuditApplicationService;
import com.example.urbanagent.audit.application.dto.AgentRunAuditView;
import com.example.urbanagent.audit.application.dto.AuditLogView;
import com.example.urbanagent.audit.application.dto.ModelCallAuditView;
import com.example.urbanagent.audit.application.dto.QueryRecordAuditView;
import com.example.urbanagent.audit.application.dto.RiskEventAuditView;
import com.example.urbanagent.audit.application.dto.ToolCallAuditView;
import com.example.urbanagent.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit", description = "运行、工具、问数、风险和模型调用审计接口")
public class AuditController {

    private final AuditApplicationService auditApplicationService;

    public AuditController(AuditApplicationService auditApplicationService) {
        this.auditApplicationService = auditApplicationService;
    }

    @GetMapping("/agent-runs")
    @Operation(summary = "查询智能体运行审计", description = "查询最近的智能体运行记录")
    public ApiResponse<List<AgentRunAuditView>> listRuns() {
        return ApiResponse.success(auditApplicationService.listRuns());
    }

    @GetMapping("/tool-calls")
    @Operation(summary = "查询工具调用审计", description = "查询最近的工具调用记录")
    public ApiResponse<List<ToolCallAuditView>> listToolCalls() {
        return ApiResponse.success(auditApplicationService.listToolCalls());
    }

    @GetMapping("/data-access")
    @Operation(summary = "查询问数审计", description = "查询最近的问数预览和执行记录")
    public ApiResponse<List<QueryRecordAuditView>> listDataAccess() {
        return ApiResponse.success(auditApplicationService.listDataAccess());
    }

    @GetMapping("/risk-events")
    @Operation(summary = "查询风险审计", description = "查询最近的风险事件记录")
    public ApiResponse<List<RiskEventAuditView>> listRiskEvents() {
        return ApiResponse.success(auditApplicationService.listRiskEvents());
    }

    @GetMapping("/model-calls")
    @Operation(summary = "查询模型调用审计", description = "查询最近的模型调用耗时、状态和错误记录")
    public ApiResponse<List<ModelCallAuditView>> listModelCalls() {
        return ApiResponse.success(auditApplicationService.listModelCalls());
    }

    @GetMapping("/logs")
    @Operation(summary = "统一追溯审计", description = "按 runId、taskId、toolCallId、queryId、evidenceId 查询统一审计日志")
    public ApiResponse<List<AuditLogView>> searchLogs(@Parameter(description = "运行 ID") @RequestParam(required = false) String runId,
                                                      @Parameter(description = "计划步骤 ID") @RequestParam(required = false) String taskId,
                                                      @Parameter(description = "工具调用 ID") @RequestParam(required = false) String toolCallId,
                                                      @Parameter(description = "问数记录 ID") @RequestParam(required = false) String queryId,
                                                      @Parameter(description = "证据或知识文档 ID") @RequestParam(required = false) String evidenceId,
                                                      @Parameter(description = "返回条数") @RequestParam(defaultValue = "20") Integer limit) {
        return ApiResponse.success(auditApplicationService.searchLogs(runId, taskId, toolCallId, queryId, evidenceId, limit));
    }
}
