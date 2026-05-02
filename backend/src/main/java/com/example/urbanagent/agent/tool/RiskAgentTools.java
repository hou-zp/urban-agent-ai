package com.example.urbanagent.agent.tool;

import com.example.urbanagent.risk.application.RiskAssessment;
import com.example.urbanagent.risk.application.RiskWorkflowService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class RiskAgentTools {

    private final RiskWorkflowService riskWorkflowService;

    public RiskAgentTools(RiskWorkflowService riskWorkflowService) {
        this.riskWorkflowService = riskWorkflowService;
    }

    @Tool(name = "risk_assess", description = "评估问题是否涉及高风险法律事项")
    @org.springframework.ai.tool.annotation.Tool(name = "risk_assess", description = "评估问题是否涉及高风险法律事项")
    public String riskAssess(@ToolParam(name = "question", description = "待评估的问题")
                             @org.springframework.ai.tool.annotation.ToolParam(description = "待评估的问题", required = true)
                             String question) {
        RiskAssessment assessment = riskWorkflowService.assess(question);
        return "risk_level=" + assessment.riskLevel().name()
                + ", categories=" + assessment.categories()
                + ", review_required=" + assessment.reviewRequired()
                + ", reason=" + assessment.triggerReason();
    }

    @Tool(name = "user_confirm", description = "请求用户确认敏感动作")
    @org.springframework.ai.tool.annotation.Tool(name = "user_confirm", description = "请求用户确认敏感动作")
    public String userConfirm(@ToolParam(name = "action", description = "待确认动作")
                              @org.springframework.ai.tool.annotation.ToolParam(description = "待确认动作", required = true)
                              String action,
                              @ToolParam(name = "reason", description = "需要确认的原因")
                              @org.springframework.ai.tool.annotation.ToolParam(description = "需要确认的原因", required = true)
                              String reason) {
        return "需要用户确认，动作已暂停。action=" + action + ", reason=" + reason;
    }
}
