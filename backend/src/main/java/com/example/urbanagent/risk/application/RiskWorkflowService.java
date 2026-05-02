package com.example.urbanagent.risk.application;

import com.example.urbanagent.agent.domain.AgentRun;
import com.example.urbanagent.audit.application.AuditLogService;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.example.urbanagent.risk.domain.LegalReview;
import com.example.urbanagent.risk.domain.LegalReviewStatus;
import com.example.urbanagent.risk.domain.RiskCategory;
import com.example.urbanagent.risk.domain.RiskEvent;
import com.example.urbanagent.risk.domain.RiskLevel;
import com.example.urbanagent.risk.repository.LegalReviewRepository;
import com.example.urbanagent.risk.repository.RiskEventRepository;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
public class RiskWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(RiskWorkflowService.class);

    private final RiskAssessmentService riskAssessmentService;
    private final PromptAttackProtectionService promptAttackProtectionService;
    private final RiskEventRepository riskEventRepository;
    private final LegalReviewRepository legalReviewRepository;
    private final AuditLogService auditLogService;

    public RiskWorkflowService(RiskAssessmentService riskAssessmentService,
                               PromptAttackProtectionService promptAttackProtectionService,
                               RiskEventRepository riskEventRepository,
                               LegalReviewRepository legalReviewRepository,
                               AuditLogService auditLogService) {
        this.riskAssessmentService = riskAssessmentService;
        this.promptAttackProtectionService = promptAttackProtectionService;
        this.riskEventRepository = riskEventRepository;
        this.legalReviewRepository = legalReviewRepository;
        this.auditLogService = auditLogService;
    }

    public RiskAssessment assess(String question) {
        return riskAssessmentService.assess(question);
    }

    @Observed(name = "urban.risk.prompt-guard")
    @Transactional
    public PromptAttackBlockResult createPromptAttackBlockIfRequired(String sessionId, AgentRun run, String question) {
        PromptAttackAssessment assessment = promptAttackProtectionService.assess(question);
        if (!assessment.blocked()) {
            return null;
        }

        RiskEvent riskEvent = riskEventRepository.save(new RiskEvent(
                run.getId(),
                sessionId,
                UserContextHolder.get().userId(),
                question,
                RiskLevel.HIGH,
                RiskCategory.PROMPT_INJECTION.name(),
                assessment.triggerReason(),
                false
        ));
        auditLogService.recordRiskEvent("risk.prompt_guard", riskEvent);

        String assistantMessage = """
                当前请求涉及提示词注入、越权探测或内部配置泄露，系统已拒绝处理。
                我不会输出系统提示词、模型密钥、内部工具权限或未授权的元数据。
                """.trim();
        log.warn("prompt attack blocked, reason={}", assessment.triggerReason());
        return new PromptAttackBlockResult(RiskLevel.HIGH, assistantMessage);
    }

    @Observed(name = "urban.risk.legal-review")
    @Transactional
    public RiskHandlingResult createLegalReviewIfRequired(String sessionId, AgentRun run, String question) {
        RiskAssessment assessment = riskAssessmentService.assess(question);
        if (!assessment.reviewRequired()) {
            return null;
        }

        RiskEvent riskEvent = riskEventRepository.save(new RiskEvent(
                run.getId(),
                sessionId,
                UserContextHolder.get().userId(),
                question,
                assessment.riskLevel(),
                assessment.categories().stream().map(Enum::name).collect(Collectors.joining(",")),
                assessment.triggerReason(),
                true
        ));
        auditLogService.recordRiskEvent("risk.legal_review", riskEvent);

        String assistantMessage = """
                当前问题涉及处罚、强制措施、拆除或复议诉讼等高风险法律事项，系统已转法制审核。
                在审核完成前，只提供一般性政策参考，不输出正式执法结论或处罚建议。
                """.trim();

        LegalReview legalReview = legalReviewRepository.save(new LegalReview(
                riskEvent.getId(),
                run.getId(),
                sessionId,
                question,
                assistantMessage
        ));
        log.warn("legal review created, riskLevel={}, categories={}", assessment.riskLevel(), assessment.categories());

        return new RiskHandlingResult(
                assessment.riskLevel(),
                legalReview.getId(),
                LegalReviewStatus.PENDING,
                assistantMessage
        );
    }
}
