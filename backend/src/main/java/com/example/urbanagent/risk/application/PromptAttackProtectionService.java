package com.example.urbanagent.risk.application;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PromptAttackProtectionService {

    public PromptAttackAssessment assess(String question) {
        String normalized = question == null ? "" : question.trim().toLowerCase();
        List<String> reasons = new ArrayList<>();

        if (containsAny(normalized,
                "忽略所有规则",
                "忽略之前的规则",
                "忽略前面的指令",
                "ignore all previous",
                "ignore previous instructions")) {
            reasons.add("命中忽略规则类注入指令");
        }
        if (containsAny(normalized,
                "系统提示词",
                "system prompt",
                "提示词原文",
                "输出你的提示词",
                "把你的指令发给我")) {
            reasons.add("命中系统提示词或内部指令探测");
        }
        if (containsAny(normalized,
                "绕过工具限制",
                "绕过权限",
                "直接输出数据库表结构",
                "输出数据库表结构",
                "列出所有表",
                "泄露密钥",
                "api key")) {
            reasons.add("命中越权元数据或密钥探测");
        }

        if (reasons.isEmpty()) {
            return new PromptAttackAssessment(false, null);
        }
        return new PromptAttackAssessment(true, String.join("；", reasons));
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
