package com.example.urbanagent.agent.controller;

import com.example.urbanagent.agent.application.ChatApplicationService;
import com.example.urbanagent.agent.application.RunApplicationService;
import com.example.urbanagent.agent.application.SessionApplicationService;
import com.example.urbanagent.agent.application.dto.CreateSessionRequest;
import com.example.urbanagent.agent.application.dto.MessageView;
import com.example.urbanagent.agent.application.dto.PlanView;
import com.example.urbanagent.agent.application.dto.RunView;
import com.example.urbanagent.agent.application.dto.SendMessageRequest;
import com.example.urbanagent.agent.application.dto.SessionView;
import com.example.urbanagent.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent/sessions")
@Tag(name = "Agent Sessions", description = "智能体会话、消息、流式对话和计划执行接口")
public class AgentSessionController {

    private final SessionApplicationService sessionService;
    private final ChatApplicationService chatService;
    private final RunApplicationService runApplicationService;

    public AgentSessionController(SessionApplicationService sessionService,
                                  ChatApplicationService chatService,
                                  RunApplicationService runApplicationService) {
        this.sessionService = sessionService;
        this.chatService = chatService;
        this.runApplicationService = runApplicationService;
    }

    @PostMapping
    @Operation(summary = "创建会话", description = "为当前用户创建一个智能体对话会话")
    public ApiResponse<SessionView> createSession(@Valid @RequestBody CreateSessionRequest request) {
        return ApiResponse.success(sessionService.createSession(request.title()));
    }

    @GetMapping
    @Operation(summary = "查询会话列表", description = "查询当前用户可见的智能体会话")
    public ApiResponse<List<SessionView>> listSessions() {
        return ApiResponse.success(sessionService.listSessions());
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "查询会话详情", description = "查询会话基础信息和消息列表")
    public ApiResponse<SessionView> getSession(@Parameter(description = "会话 ID") @PathVariable String sessionId) {
        return ApiResponse.success(sessionService.getSession(sessionId));
    }

    @PostMapping("/{sessionId}/messages")
    @Operation(summary = "发送消息", description = "发送非流式问题并返回智能体回答")
    public ApiResponse<MessageView> sendMessage(@Parameter(description = "会话 ID") @PathVariable String sessionId,
                                                @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.success(chatService.chat(sessionId, request.content()));
    }

    @PostMapping(path = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式发送消息", description = "通过 SSE 返回智能体流式回答")
    public SseEmitter streamMessage(@Parameter(description = "会话 ID") @PathVariable String sessionId,
                                    @Valid @RequestBody SendMessageRequest request) {
        return chatService.streamChat(sessionId, request.content());
    }

    @PostMapping("/{sessionId}/cancel")
    @Operation(summary = "取消当前运行", description = "取消指定会话最近一次运行中的任务")
    public ApiResponse<RunView> cancel(@Parameter(description = "会话 ID") @PathVariable String sessionId) {
        return ApiResponse.success(runApplicationService.cancelSessionRun(sessionId));
    }

    @PostMapping("/{sessionId}/resume")
    @Operation(summary = "恢复当前运行", description = "恢复指定会话可恢复的智能体运行")
    public ApiResponse<MessageView> resume(@Parameter(description = "会话 ID") @PathVariable String sessionId) {
        return ApiResponse.success(runApplicationService.resumeSessionRun(sessionId));
    }

    @GetMapping("/runs/{runId}")
    @Operation(summary = "查询运行记录", description = "查询一次智能体运行的状态、风险和错误信息")
    public ApiResponse<RunView> getRun(@Parameter(description = "运行 ID") @PathVariable String runId) {
        return ApiResponse.success(runApplicationService.getRun(runId));
    }

    @GetMapping("/runs/{runId}/plan")
    @Operation(summary = "查询运行计划", description = "查询复杂任务的计划和步骤状态")
    public ApiResponse<PlanView> getRunPlan(@Parameter(description = "运行 ID") @PathVariable String runId) {
        return ApiResponse.success(runApplicationService.getRunPlan(runId));
    }

    @PostMapping("/runs/{runId}/plan/execute-next")
    @Operation(summary = "执行下一计划步骤", description = "按计划顺序推进下一步可执行步骤")
    public ApiResponse<PlanView> executeNextPlanStep(@Parameter(description = "运行 ID") @PathVariable String runId) {
        return ApiResponse.success(runApplicationService.executeNextPlanStep(runId));
    }

    @PostMapping("/runs/{runId}/plan/steps/{stepId}/execute")
    @Operation(summary = "执行指定计划步骤", description = "用于前端指定推进或重试失败步骤")
    public ApiResponse<PlanView> executePlanStep(@Parameter(description = "运行 ID") @PathVariable String runId,
                                                 @Parameter(description = "计划步骤 ID") @PathVariable String stepId) {
        return ApiResponse.success(runApplicationService.executePlanStep(runId, stepId));
    }
}
