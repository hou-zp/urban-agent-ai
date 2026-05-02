package com.example.urbanagent.agent.application.dto;

import java.util.List;

/**
 * 闸门检查结果。
 * 用于 FinalGuardrailService 返回检查结果。
 */
public record GuardrailResult(
        boolean success,           // 是否通过检查
        List<Violation> violations, // 违规列表
        String safeMessage       // 被拦截时的安全答案
) {
    /**
     * 创建通过结果。
     */
    public static GuardrailResult passed() {
        return new GuardrailResult(true, List.of(), null);
    }

    /**
     * 创建拦截结果。
     */
    public static GuardrailResult blocked(List<Violation> violations, String safeMessage) {
        return new GuardrailResult(false, violations, safeMessage);
    }

    /**
     * 创建拦截结果（使用默认安全消息）。
     */
    public static GuardrailResult blocked(List<Violation> violations) {
        return blocked(violations, defaultSafeMessage(violations));
    }

    /**
     * 检查是否被拦截。
     */
    public boolean blocked() {
        return !success;
    }

    private static String defaultSafeMessage(List<Violation> violations) {
        if (violations == null || violations.isEmpty()) {
            return "答案未通过安全检查，请调整查询范围或补充数据。";
        }
        // 根据违规类型生成安全消息
        boolean hasDataViolation = violations.stream()
                .anyMatch(v -> v.type() == ViolationType.DATA_CLAIM_WITHOUT_QUERY);
        boolean hasLawViolation = violations.stream()
                .anyMatch(v -> v.type() == ViolationType.LAW_WITHOUT_EVIDENCE);
        boolean hasPolicyViolation = violations.stream()
                .anyMatch(v -> v.type() == ViolationType.POLICY_WITHOUT_EVIDENCE);
        boolean hasSensitiveViolation = violations.stream()
                .anyMatch(v -> v.type() == ViolationType.SENSITIVE_FIELD_EXPOSED);

        if (hasDataViolation && hasLawViolation) {
            return "本次问题涉及数据性结论和法规依据，但本轮未获得有效业务数据查询结果和法规检索结果，因此不能给出具体数量或法规结论。请确认数据源是否已接入、当前账号是否具备权限，或补充查询范围后重试。";
        }
        if (hasDataViolation) {
            return "本次问题涉及数据性结论，但本轮未获得有效业务数据查询结果，因此不能给出具体数量、趋势、排名或状态。请确认数据源是否已接入、当前账号是否具备权限，或补充查询范围后重试。";
        }
        if (hasLawViolation || hasPolicyViolation) {
            return "本次问题涉及政策法规结论，但本轮未获得有效检索结果，因此不能给出正式政策法规结论。请确认政策法规库是否已接入、关键词是否准确，或转人工核实。";
        }
        if (hasSensitiveViolation) {
            return "本次查询结果含敏感字段，已阻断输出。请调整查询范围或联系管理员开通相应权限。";
        }
        return "答案未通过安全检查，请调整查询范围或补充数据。";
    }

    /**
     * 违规类型。
     */
    public enum ViolationType {
        DATA_CLAIM_WITHOUT_QUERY,      // 数据声明无查询编号
        LAW_WITHOUT_EVIDENCE,           // 法规结论无证据
        POLICY_WITHOUT_EVIDENCE,       // 政策结论无证据
        SENSITIVE_FIELD_EXPOSED,       // 敏感字段暴露
        CHART_WITHOUT_QUERY,          // 图表无查询编号
        EMPTY_RESULT_ASSUMED,         // 空结果被假设
        CROSS_REGION_ACCESS            // 跨区域访问
    }

    /**
     * 违规记录。
     */
    public record Violation(
            ViolationType type,     // 违规类型
            String claimText,       // 涉及的具体声明
            String queryId,         // 关联的查询编号
            String evidenceId       // 关联的证据编号
    ) {
        public static Violation dataClaimWithoutQuery(String claimText) {
            return new Violation(ViolationType.DATA_CLAIM_WITHOUT_QUERY, claimText, null, null);
        }

        public static Violation lawWithoutEvidence(String claimText) {
            return new Violation(ViolationType.LAW_WITHOUT_EVIDENCE, claimText, null, null);
        }

        public static Violation policyWithoutEvidence(String claimText) {
            return new Violation(ViolationType.POLICY_WITHOUT_EVIDENCE, claimText, null, null);
        }

        public static Violation chartWithoutQuery(String claimText, String queryId) {
            return new Violation(ViolationType.CHART_WITHOUT_QUERY, claimText, queryId, null);
        }

        public static Violation sensitiveField(String claimText) {
            return new Violation(ViolationType.SENSITIVE_FIELD_EXPOSED, claimText, null, null);
        }
    }
}