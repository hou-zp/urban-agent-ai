package com.example.urbanagent.agent.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * 答案草稿结构。
 * 用于结构化追踪答案各部分来源，支持六段式答案生成和防造假验证。
 */
public record AnswerDraft(
        String conclusion,                // 1. 结论摘要
        List<String> dataFindings,      // 2. 数据结果（queryId 绑定）
        List<String> policyFindings,    // 3. 政策法规依据（evidenceId 绑定）
        List<String> lawFindings,       // 3. 法律法规依据（evidenceId 绑定）
        List<String> businessJudgements,// 4. 业务判断
        List<String> suggestions,       // 5. 处置建议
        List<String> limitations,       // 6. 口径、来源和风险提示
        List<EvidenceRef> evidenceRefs,// 证据引用列表
        List<DataClaim> dataClaims,    // 数据声明列表（用于防造假验证）
        Instant createdAt               // 创建时间
) {
    /**
     * 检查是否包含未验证的数据声明。
     * 用于 FinalGuardrailService 拦截无 queryId 的数字结论。
     */
    public List<DataClaim> unsupportedClaims() {
        return dataClaims.stream()
                .filter(claim -> !claim.isVerifiable())
                .toList();
    }

    /**
     * 检查是否包含无来源的政策法规结论。
     */
    public boolean hasUnattributedLegalContent() {
        return (policyFindings == null || policyFindings.isEmpty()) &&
               (lawFindings == null || lawFindings.isEmpty()) &&
               evidenceRefs.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String conclusion;
        private List<String> dataFindings = List.of();
        private List<String> policyFindings = List.of();
        private List<String> lawFindings = List.of();
        private List<String> businessJudgements = List.of();
        private List<String> suggestions = List.of();
        private List<String> limitations = List.of();
        private List<EvidenceRef> evidenceRefs = List.of();
        private List<DataClaim> dataClaims = List.of();

        public Builder conclusion(String conclusion) {
            this.conclusion = conclusion;
            return this;
        }

        public Builder dataFindings(List<String> dataFindings) {
            this.dataFindings = dataFindings;
            return this;
        }

        public Builder policyFindings(List<String> policyFindings) {
            this.policyFindings = policyFindings;
            return this;
        }

        public Builder lawFindings(List<String> lawFindings) {
            this.lawFindings = lawFindings;
            return this;
        }

        public Builder businessJudgements(List<String> businessJudgements) {
            this.businessJudgements = businessJudgements;
            return this;
        }

        public Builder suggestions(List<String> suggestions) {
            this.suggestions = suggestions;
            return this;
        }

        public Builder limitations(List<String> limitations) {
            this.limitations = limitations;
            return this;
        }

        public Builder evidenceRefs(List<EvidenceRef> evidenceRefs) {
            this.evidenceRefs = evidenceRefs;
            return this;
        }

        public Builder dataClaims(List<DataClaim> dataClaims) {
            this.dataClaims = dataClaims;
            return this;
        }

        public AnswerDraft build() {
            return new AnswerDraft(
                    conclusion,
                    List.copyOf(dataFindings),
                    List.copyOf(policyFindings),
                    List.copyOf(lawFindings),
                    List.copyOf(businessJudgements),
                    List.copyOf(suggestions),
                    List.copyOf(limitations),
                    List.copyOf(evidenceRefs),
                    List.copyOf(dataClaims),
                    Instant.now()
            );
        }
    }
}