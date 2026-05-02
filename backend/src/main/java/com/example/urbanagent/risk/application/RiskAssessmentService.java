package com.example.urbanagent.risk.application;

import com.example.urbanagent.risk.domain.RiskCategory;
import com.example.urbanagent.risk.domain.RiskLevel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RiskAssessmentService {

    public RiskAssessment assess(String question) {
        String normalized = question == null ? "" : question.trim();
        List<RiskCategory> categories = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        if (containsAny(normalized, "罚款", "处罚", "罚多少", "能罚多少钱", "罚多少钱")) {
            categories.add(RiskCategory.PENALTY);
            reasons.add("命中处罚金额相关关键词");
        }
        if (containsAny(normalized, "强制执行", "查封", "扣押", "强制措施")) {
            categories.add(RiskCategory.ENFORCEMENT);
            reasons.add("命中强制执行相关关键词");
        }
        if (containsAny(normalized, "拆除", "拆违", "清拆")) {
            categories.add(RiskCategory.DEMOLITION);
            reasons.add("命中拆除处置相关关键词");
        }
        if (containsAny(normalized, "复议", "诉讼", "起诉", "行政诉讼")) {
            categories.add(RiskCategory.RECONSIDERATION_LITIGATION);
            reasons.add("命中复议诉讼相关关键词");
        }

        if (categories.isEmpty()) {
            return new RiskAssessment(
                    RiskLevel.LOW,
                    List.of(),
                    false,
                    "未命中高风险执法或诉讼关键词",
                    "可以按知识问答流程直接回复，但仍需避免替代正式执法结论。"
            );
        }

        RiskLevel level = categories.size() >= 2 ? RiskLevel.HIGH : RiskLevel.HIGH;
        String reason = String.join("；", reasons);
        return new RiskAssessment(
                level,
                List.copyOf(categories),
                true,
                reason,
                "该问题涉及处罚、强制措施、拆除或复议诉讼，必须转法制审核后再输出正式建议。"
        );
    }

    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
