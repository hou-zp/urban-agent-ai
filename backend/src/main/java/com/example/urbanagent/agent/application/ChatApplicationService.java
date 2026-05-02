package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.ParsedQuestion;
import com.example.urbanagent.agent.application.dto.MessageCitationView;
import com.example.urbanagent.agent.application.dto.MessageView;
import com.example.urbanagent.agent.application.dto.IntentType;
import com.example.urbanagent.agent.application.dto.PlanView;
import com.example.urbanagent.agent.domain.AgentMessage;
import com.example.urbanagent.agent.domain.AgentRun;
import com.example.urbanagent.agent.domain.MessageRole;
import com.example.urbanagent.agent.domain.ToolCall;
import com.example.urbanagent.agent.repository.AgentMessageRepository;
import com.example.urbanagent.agent.repository.AgentRunRepository;
import com.example.urbanagent.agent.repository.AgentSessionRepository;
import com.example.urbanagent.agent.repository.ToolCallRepository;
import com.example.urbanagent.audit.application.AuditLogService;
import com.example.urbanagent.ai.application.ModelChunk;
import com.example.urbanagent.common.runtime.RequestRateLimiter;
import com.example.urbanagent.common.logging.LoggingContext;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.example.urbanagent.risk.application.RiskHandlingResult;
import com.example.urbanagent.risk.application.PromptAttackBlockResult;
import com.example.urbanagent.risk.application.RiskWorkflowService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Service
public class ChatApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ChatApplicationService.class);

    private final AgentSessionRepository sessionRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentRunRepository runRepository;
    private final ToolCallRepository toolCallRepository;
    private final TaskExecutor taskExecutor;
    private final MessageViewMapper messageViewMapper;
    private final ObjectMapper objectMapper;
    private final UrbanManagementAgent urbanManagementAgent;
    private final RiskWorkflowService riskWorkflowService;
    private final RequestRateLimiter requestRateLimiter;
    private final RunControlService runControlService;
    private final PlanApplicationService planApplicationService;
    private final QuestionParsingService questionParsingService;
    private final TrustedAnswerService trustedAnswerService;
    private final AuditLogService auditLogService;

    public ChatApplicationService(AgentSessionRepository sessionRepository,
                                  AgentMessageRepository messageRepository,
                                  AgentRunRepository runRepository,
                                  ToolCallRepository toolCallRepository,
                                  TaskExecutor taskExecutor,
                                  MessageViewMapper messageViewMapper,
                                  ObjectMapper objectMapper,
                                  UrbanManagementAgent urbanManagementAgent,
                                  RiskWorkflowService riskWorkflowService,
                                  RequestRateLimiter requestRateLimiter,
                                  RunControlService runControlService,
                                  PlanApplicationService planApplicationService,
                                  QuestionParsingService questionParsingService,
                                  TrustedAnswerService trustedAnswerService,
                                  AuditLogService auditLogService) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.runRepository = runRepository;
        this.toolCallRepository = toolCallRepository;
        this.taskExecutor = taskExecutor;
        this.messageViewMapper = messageViewMapper;
        this.objectMapper = objectMapper;
        this.urbanManagementAgent = urbanManagementAgent;
        this.riskWorkflowService = riskWorkflowService;
        this.requestRateLimiter = requestRateLimiter;
        this.runControlService = runControlService;
        this.planApplicationService = planApplicationService;
        this.questionParsingService = questionParsingService;
        this.trustedAnswerService = trustedAnswerService;
        this.auditLogService = auditLogService;
    }

    @Observed(name = "urban.agent.chat")
    @Transactional
    public MessageView chat(String sessionId, String content) {
        requestRateLimiter.checkChatRequest();
        assertSessionExists(sessionId);
        messageRepository.save(new AgentMessage(sessionId, MessageRole.USER, content));
        AgentRun run = runRepository.save(new AgentRun(sessionId, UserContextHolder.get().userId(), content, "urban-management-agent"));
        auditLogService.recordAgentRunStarted(run);
        LoggingContext.put("runId", run.getId());
        LoggingContext.put("sessionId", sessionId);
        log.info("agent run started");
        runControlService.register(run, null);

        try {
            MessageView view = answerWithinRun(sessionId, content, run, true);
            runControlService.complete(run.getId());
            log.info("agent run completed");
            return view;
        } catch (BusinessException ex) {
            if (ex.errorCode() == ErrorCode.RUN_CANCELLED) {
                run.cancel();
                planApplicationService.abandonPlan(run.getId(), "运行已取消");
                runRepository.save(run);
                auditLogService.recordAgentRunCancelled(run, "运行已取消");
            } else if (ex.errorCode() == ErrorCode.RUN_TIMEOUT) {
                run.fail();
                planApplicationService.abandonPlan(run.getId(), "运行超时");
                runRepository.save(run);
                auditLogService.recordAgentRunFailed(run, "运行超时");
            } else {
                run.fail();
                planApplicationService.abandonPlan(run.getId(), ex.getMessage());
                runRepository.save(run);
                auditLogService.recordAgentRunFailed(run, ex.getMessage());
            }
            runControlService.fail(run.getId());
            log.warn("agent run failed: {}", ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            run.fail();
            runRepository.save(run);
            auditLogService.recordAgentRunFailed(run, "运行异常失败");
            planApplicationService.abandonPlan(run.getId(), "运行异常失败");
            runControlService.fail(run.getId());
            log.error("agent run failed unexpectedly", ex);
            throw ex;
        } finally {
            LoggingContext.clearRunScope();
        }
    }

    @Observed(name = "urban.agent.resume")
    @Transactional
    public MessageView resumeCancelledRun(String sessionId, String content) {
        requestRateLimiter.checkChatRequest();
        AgentRun run = runRepository.save(new AgentRun(sessionId, UserContextHolder.get().userId(), content, "urban-management-agent"));
        auditLogService.recordAgentRunStarted(run);
        LoggingContext.put("runId", run.getId());
        LoggingContext.put("sessionId", sessionId);
        log.info("agent run resumed");
        runControlService.register(run, null);
        try {
            MessageView view = answerWithinRun(sessionId, content, run, false);
            runControlService.complete(run.getId());
            log.info("resumed agent run completed");
            return view;
        } catch (BusinessException ex) {
            if (ex.errorCode() == ErrorCode.RUN_CANCELLED) {
                run.cancel();
                planApplicationService.abandonPlan(run.getId(), "运行已取消");
                runRepository.save(run);
                auditLogService.recordAgentRunCancelled(run, "运行已取消");
            } else if (ex.errorCode() == ErrorCode.RUN_TIMEOUT) {
                run.fail();
                planApplicationService.abandonPlan(run.getId(), "运行超时");
                runRepository.save(run);
                auditLogService.recordAgentRunFailed(run, "运行超时");
            } else {
                run.fail();
                planApplicationService.abandonPlan(run.getId(), ex.getMessage());
                runRepository.save(run);
                auditLogService.recordAgentRunFailed(run, ex.getMessage());
            }
            runControlService.fail(run.getId());
            log.warn("resumed agent run failed: {}", ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            run.fail();
            runRepository.save(run);
            auditLogService.recordAgentRunFailed(run, "运行异常失败");
            planApplicationService.abandonPlan(run.getId(), "运行异常失败");
            runControlService.fail(run.getId());
            log.error("resumed agent run failed unexpectedly", ex);
            throw ex;
        } finally {
            LoggingContext.clearRunScope();
        }
    }

    @Observed(name = "urban.agent.stream")
    public SseEmitter streamChat(String sessionId, String content) {
        requestRateLimiter.checkChatRequest();
        assertSessionExists(sessionId);
        SseEmitter emitter = new SseEmitter(runControlService.streamTimeoutMs());
        taskExecutor.execute(() -> runStreaming(sessionId, content, emitter));
        return emitter;
    }

    @Transactional
    protected void runStreaming(String sessionId, String content, SseEmitter emitter) {
        messageRepository.save(new AgentMessage(sessionId, MessageRole.USER, content));
        AgentRun run = runRepository.save(new AgentRun(sessionId, UserContextHolder.get().userId(), content, "urban-management-agent"));
        auditLogService.recordAgentRunStarted(run);
        LoggingContext.put("runId", run.getId());
        LoggingContext.put("sessionId", sessionId);
        log.info("streaming agent run started");
        runControlService.register(run, emitter);

        try {
            ParsedQuestion parsedQuestion = questionParsingService.analyzeAndSave(run.getId(), content);
            sendEvent(emitter, "message.meta", "{\"runId\":\"" + run.getId() + "\",\"startedAt\":\"" + Instant.now() + "\"}");
            runControlService.checkRunnable(run.getId());
            PromptAttackBlockResult promptAttackBlockResult = riskWorkflowService.createPromptAttackBlockIfRequired(sessionId, run, content);
            if (promptAttackBlockResult != null) {
                saveToolCall(run.getId(), "prompt_guard", truncate(content), truncate(promptAttackBlockResult.assistantMessage()));
                AgentMessage assistantMessage = messageRepository.save(new AgentMessage(
                        sessionId,
                        MessageRole.ASSISTANT,
                        promptAttackBlockResult.assistantMessage(),
                        null,
                        promptAttackBlockResult.riskLevel(),
                        null
                ));
                sendEvent(emitter, "message.delta", promptAttackBlockResult.assistantMessage());
                run.complete();
                runRepository.save(run);
                auditLogService.recordAgentRunCompleted(run, "提示词注入已拦截");
                runControlService.complete(run.getId());
                log.info("streaming agent run blocked by prompt guard");
                sendEvent(emitter, "agent.completed", "{\"runId\":\"" + run.getId() + "\"}");
                emitter.complete();
                return;
            }

            runControlService.checkRunnable(run.getId());
            RiskHandlingResult riskHandlingResult = riskWorkflowService.createLegalReviewIfRequired(sessionId, run, content);
            if (riskHandlingResult != null) {
                saveToolCall(run.getId(), "risk_assess", truncate(content), truncate(riskHandlingResult.assistantMessage()));
                AgentMessage assistantMessage = messageRepository.save(new AgentMessage(
                        sessionId,
                        MessageRole.ASSISTANT,
                        riskHandlingResult.assistantMessage(),
                        null,
                        riskHandlingResult.riskLevel(),
                        riskHandlingResult.reviewId()
                ));
                sendEvent(emitter, "risk.pending_review", "{\"reviewId\":\"" + riskHandlingResult.reviewId() + "\",\"riskLevel\":\"" + riskHandlingResult.riskLevel() + "\"}");
                sendEvent(emitter, "message.delta", riskHandlingResult.assistantMessage());
                run.pendingLegalReview();
                runRepository.save(run);
                auditLogService.recordAgentRunPendingReview(run, riskHandlingResult.reviewId());
                runControlService.complete(run.getId());
                log.info("streaming agent run moved to legal review");
                sendEvent(emitter, "agent.completed", "{\"runId\":\"" + run.getId() + "\"}");
                emitter.complete();
                return;
            }

            runControlService.checkRunnable(run.getId());
            var planView = planApplicationService.createPlanIfNeeded(run);
            if (planView != null) {
                sendEvent(emitter, "plan.updated", objectMapper.writeValueAsString(planView));
                if (planApplicationService.requiresManualExecution(planView)) {
                    if (shouldAutoExecutePlan(content, parsedQuestion)) {
                        var executionResult = planApplicationService.executePlanToAnswer(run.getId());
                        sendEvent(emitter, "plan.updated", objectMapper.writeValueAsString(executionResult.planView()));
                        StringBuilder builder = new StringBuilder();
                        for (ModelChunk chunk : streamFromAnswer(executionResult.answerMessage().getContent())) {
                            runControlService.checkRunnable(run.getId());
                            if (chunk.done()) {
                                sendEvent(emitter, "agent.completed", "{\"runId\":\"" + run.getId() + "\"}");
                                break;
                            }
                            builder.append(chunk.text());
                            sendEvent(emitter, "message.delta", chunk.text());
                            runControlService.pauseChunk(run.getId());
                        }
                        saveToolCall(run.getId(), "plan_autorun", truncate(content), truncate(builder.toString()));
                        run.complete();
                        runRepository.save(run);
                        auditLogService.recordAgentRunCompleted(run, "综合分析计划已自动执行完成");
                        runControlService.complete(run.getId());
                        log.info("streaming agent run completed after plan auto execution");
                        emitter.complete();
                        return;
                    }
                    String planningReply = planApplicationService.buildPlanningReply(planView);
                    messageRepository.save(new AgentMessage(sessionId, MessageRole.ASSISTANT, planningReply));
                    saveToolCall(run.getId(), "plan_generator", truncate(content), truncate(planningReply));
                    run.complete();
                    runRepository.save(run);
                    auditLogService.recordAgentRunCompleted(run, "生成执行计划并等待人工推进");
                    runControlService.complete(run.getId());
                    log.info("streaming agent run completed with execution plan");
                    sendEvent(emitter, "message.delta", planningReply);
                    sendEvent(emitter, "agent.completed", "{\"runId\":\"" + run.getId() + "\"}");
                    emitter.complete();
                    return;
                }
            }

            runControlService.checkRunnable(run.getId());
            AgentAnswer answer = answerByParsedQuestion(run.getId(), sessionId, content, parsedQuestion);
            var updatedPlanView = planApplicationService.completePlanAfterAnswer(run.getId(), answer);
            if (updatedPlanView != null) {
                sendEvent(emitter, "plan.updated", objectMapper.writeValueAsString(updatedPlanView));
            }
            StringBuilder builder = new StringBuilder();
            for (ModelChunk chunk : streamFromAnswer(answer.content())) {
                runControlService.checkRunnable(run.getId());
                if (chunk.done()) {
                    sendEvent(emitter, "agent.completed", "{\"runId\":\"" + run.getId() + "\"}");
                    break;
                }
                builder.append(chunk.text());
                sendEvent(emitter, "message.delta", chunk.text());
                runControlService.pauseChunk(run.getId());
            }
            AgentMessage assistantMessage = messageRepository.save(new AgentMessage(
                    sessionId,
                    MessageRole.ASSISTANT,
                    builder.toString(),
                    writeCitations(answer.citations()),
                    writeStructuredAnswer(answer.composedAnswer()),
                    answer.riskLevel(),
                    answer.reviewId()
            ));
            saveToolCall(run.getId(), "urban-management-agent.streamChat", truncate(content), truncate(builder.toString()));
            run.complete();
            runRepository.save(run);
            auditLogService.recordAgentRunCompleted(run, "流式回答完成");
            runControlService.complete(run.getId());
            log.info("streaming agent run completed");
            emitter.complete();
        } catch (BusinessException ex) {
            if (ex.errorCode() == ErrorCode.RUN_CANCELLED) {
                run.cancel();
                planApplicationService.abandonPlan(run.getId(), "运行已取消");
                runRepository.save(run);
                auditLogService.recordAgentRunCancelled(run, "运行已取消");
            } else if (ex.errorCode() == ErrorCode.RUN_TIMEOUT) {
                run.fail();
                planApplicationService.abandonPlan(run.getId(), "运行超时");
                runRepository.save(run);
                auditLogService.recordAgentRunFailed(run, "运行超时");
            } else {
                run.fail();
                planApplicationService.abandonPlan(run.getId(), ex.getMessage());
                runRepository.save(run);
                auditLogService.recordAgentRunFailed(run, ex.getMessage());
            }
            runControlService.fail(run.getId());
            log.warn("streaming agent run stopped: {}", ex.getMessage());
            try {
                String eventName = ex.errorCode() == ErrorCode.RUN_CANCELLED ? "agent.cancelled" : "agent.failed";
                sendEvent(emitter, eventName, ex.getMessage());
            } catch (IOException ignored) {
                // ignore secondary transport failure
            }
            if (ex.errorCode() != ErrorCode.RUN_CANCELLED) {
                messageRepository.save(new AgentMessage(sessionId, MessageRole.ASSISTANT, ex.getMessage()));
            }
            emitter.complete();
        } catch (Exception ex) {
            run.fail();
            runRepository.save(run);
            auditLogService.recordAgentRunFailed(run, "运行异常失败");
            planApplicationService.abandonPlan(run.getId(), "运行异常失败");
            runControlService.fail(run.getId());
            log.error("streaming agent run failed unexpectedly", ex);
            try {
                sendEvent(emitter, "agent.failed", ex.getMessage());
            } catch (IOException ignored) {
                // ignore secondary transport failure
            }
            messageRepository.save(new AgentMessage(sessionId, MessageRole.ASSISTANT, "服务暂时异常，请稍后再试。"));
            emitter.complete();
        } finally {
            LoggingContext.clearRunScope();
        }
    }

    private MessageView answerWithinRun(String sessionId, String content, AgentRun run, boolean persistUserMessage) {
        ParsedQuestion parsedQuestion = questionParsingService.analyzeAndSave(run.getId(), content);
        runControlService.checkRunnable(run.getId());
        PromptAttackBlockResult promptAttackBlockResult = riskWorkflowService.createPromptAttackBlockIfRequired(sessionId, run, content);
        if (promptAttackBlockResult != null) {
            saveToolCall(run.getId(), "prompt_guard", truncate(content), truncate(promptAttackBlockResult.assistantMessage()));
            run.complete();
            runRepository.save(run);
            auditLogService.recordAgentRunCompleted(run, "提示词注入已拦截");
            AgentMessage assistantMessage = messageRepository.save(new AgentMessage(
                    sessionId,
                    MessageRole.ASSISTANT,
                    promptAttackBlockResult.assistantMessage(),
                    null,
                    promptAttackBlockResult.riskLevel(),
                    null
            ));
            return messageViewMapper.toView(assistantMessage, run.getId(), null);
        }

        runControlService.checkRunnable(run.getId());
        RiskHandlingResult riskHandlingResult = riskWorkflowService.createLegalReviewIfRequired(sessionId, run, content);
        if (riskHandlingResult != null) {
            saveToolCall(run.getId(), "risk_assess", truncate(content), truncate(riskHandlingResult.assistantMessage()));
            run.pendingLegalReview();
            runRepository.save(run);
            auditLogService.recordAgentRunPendingReview(run, riskHandlingResult.reviewId());
            AgentMessage assistantMessage = messageRepository.save(new AgentMessage(
                    sessionId,
                    MessageRole.ASSISTANT,
                    riskHandlingResult.assistantMessage(),
                    null,
                    riskHandlingResult.riskLevel(),
                    riskHandlingResult.reviewId()
            ));
            return messageViewMapper.toView(assistantMessage, run.getId(), null);
        }

        runControlService.checkRunnable(run.getId());
        var planView = planApplicationService.createPlanIfNeeded(run);
        if (planView != null && planApplicationService.requiresManualExecution(planView)) {
            if (shouldAutoExecutePlan(content, parsedQuestion)) {
                return autoExecutePlanAndReturnMessage(sessionId, content, run);
            }
            String planningReply = planApplicationService.buildPlanningReply(planView);
            AgentMessage assistantMessage = messageRepository.save(new AgentMessage(
                    sessionId,
                    MessageRole.ASSISTANT,
                    planningReply
            ));
            saveToolCall(run.getId(), "plan_generator", truncate(content), truncate(planningReply));
            run.complete();
            runRepository.save(run);
            auditLogService.recordAgentRunCompleted(run, "生成执行计划并等待人工推进");
            return messageViewMapper.toView(assistantMessage, run.getId(), planView.id());
        }

        runControlService.checkRunnable(run.getId());
        AgentAnswer answer = answerByParsedQuestion(run.getId(), sessionId, content, parsedQuestion);
        var updatedPlanView = planApplicationService.completePlanAfterAnswer(run.getId(), answer);
        AgentMessage assistantMessage = messageRepository.save(new AgentMessage(
                sessionId,
                MessageRole.ASSISTANT,
                answer.content(),
                writeCitations(answer.citations()),
                writeStructuredAnswer(answer.composedAnswer()),
                answer.riskLevel(),
                answer.reviewId()
        ));
        saveToolCall(run.getId(), persistUserMessage ? "urban-management-agent.chat" : "urban-management-agent.resume", truncate(content), truncate(answer.content()));
        run.complete();
        runRepository.save(run);
        auditLogService.recordAgentRunCompleted(run, persistUserMessage ? "非流式回答完成" : "恢复运行完成");
        return messageViewMapper.toView(assistantMessage, run.getId(), updatedPlanView == null ? null : updatedPlanView.id());
    }

    private MessageView autoExecutePlanAndReturnMessage(String sessionId, String content, AgentRun run) {
        var executionResult = planApplicationService.executePlanToAnswer(run.getId());
        AgentMessage assistantMessage = executionResult.answerMessage();
        saveToolCall(run.getId(), "plan_autorun", truncate(content), truncate(assistantMessage.getContent()));
        run.complete();
        runRepository.save(run);
        auditLogService.recordAgentRunCompleted(run, "综合分析计划已自动执行完成");
        return messageViewMapper.toView(assistantMessage, run.getId(), executionResult.planView().id());
    }

    private boolean shouldAutoExecutePlan(String content, ParsedQuestion parsedQuestion) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", "");
        return parsedQuestion.hasMandatoryDataIntent()
                && !normalized.contains("综合分析")
                && (parsedQuestion.requiresCitation()
                || parsedQuestion.hasAnyIntent(IntentType.LEGAL_ADVICE, IntentType.BUSINESS_CONSULTATION));
    }

    private void assertSessionExists(String sessionId) {
        if (!sessionRepository.existsByIdAndUserId(sessionId, UserContextHolder.get().userId())) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
    }

    private List<String> loadHistory(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(AgentMessage::getContent)
                .toList();
    }

    private void sendEvent(SseEmitter emitter, String eventName, String payload) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(payload));
    }

    private List<ModelChunk> streamFromAnswer(String content) {
        String[] tokens = content.split(" ");
        java.util.ArrayList<ModelChunk> chunks = new java.util.ArrayList<>();
        for (int index = 0; index < tokens.length; index++) {
            chunks.add(new ModelChunk(tokens[index] + (index == tokens.length - 1 ? "" : " "), false));
        }
        chunks.add(new ModelChunk("", true));
        return chunks;
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

    private String writeStructuredAnswer(com.example.urbanagent.agent.application.dto.ComposedAnswer composedAnswer) {
        if (composedAnswer == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(composedAnswer);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private AgentAnswer answerByParsedQuestion(String runId, String sessionId, String content, ParsedQuestion parsedQuestion) {
        boolean primaryDataIntent = parsedQuestion.primaryIntent()
                .map(intentType -> intentType == com.example.urbanagent.agent.application.dto.IntentType.METRIC_QUERY
                        || intentType == com.example.urbanagent.agent.application.dto.IntentType.BUSINESS_DATA_QUERY)
                .orElse(false);
        if (parsedQuestion.hasMandatoryDataIntent() && primaryDataIntent) {
            return trustedAnswerService.answer(runId, content, parsedQuestion);
        }
        return urbanManagementAgent.answer(runId, loadHistory(sessionId), content);
    }

    private String truncate(String content) {
        return content.length() <= 200 ? content : content.substring(0, 200);
    }

    private ToolCall saveToolCall(String runId, String toolName, String inputSummary, String outputSummary) {
        ToolCall toolCall = toolCallRepository.save(new ToolCall(runId, toolName, inputSummary, outputSummary));
        auditLogService.recordToolCall(toolCall, null, null, null);
        return toolCall;
    }
}
