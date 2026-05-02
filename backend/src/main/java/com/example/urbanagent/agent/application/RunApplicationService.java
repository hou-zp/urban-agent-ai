package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.MessageView;
import com.example.urbanagent.agent.application.dto.PlanView;
import com.example.urbanagent.agent.application.dto.RunView;
import com.example.urbanagent.agent.domain.AgentRun;
import com.example.urbanagent.agent.repository.AgentRunRepository;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunApplicationService {

    private final AgentRunRepository agentRunRepository;
    private final RunControlService runControlService;
    private final ChatApplicationService chatApplicationService;
    private final PlanApplicationService planApplicationService;

    public RunApplicationService(AgentRunRepository agentRunRepository,
                                 RunControlService runControlService,
                                 ChatApplicationService chatApplicationService,
                                 PlanApplicationService planApplicationService) {
        this.agentRunRepository = agentRunRepository;
        this.runControlService = runControlService;
        this.chatApplicationService = chatApplicationService;
        this.planApplicationService = planApplicationService;
    }

    @Transactional(readOnly = true)
    public RunView getRun(String runId) {
        AgentRun run = agentRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));
        return RunView.from(run);
    }

    @Transactional
    public RunView cancelSessionRun(String sessionId) {
        return RunView.from(runControlService.cancelLatestRun(sessionId));
    }

    @Transactional
    public MessageView resumeSessionRun(String sessionId) {
        AgentRun cancelledRun = runControlService.getLatestResumableRun(sessionId);
        return chatApplicationService.resumeCancelledRun(sessionId, cancelledRun.getQuestion());
    }

    @Transactional(readOnly = true)
    public PlanView getRunPlan(String runId) {
        return planApplicationService.getPlanByRunId(runId);
    }

    public PlanView executeNextPlanStep(String runId) {
        return planApplicationService.executeNextStep(runId);
    }

    public PlanView executePlanStep(String runId, String stepId) {
        return planApplicationService.executeStep(runId, stepId);
    }
}
