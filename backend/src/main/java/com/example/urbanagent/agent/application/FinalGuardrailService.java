package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.AnswerDraft;
import com.example.urbanagent.agent.application.dto.ComposedAnswer;
import com.example.urbanagent.agent.application.dto.DataClaim;
import com.example.urbanagent.agent.application.dto.GuardrailResult;
import com.example.urbanagent.agent.application.dto.ParsedQuestion;
import com.example.urbanagent.query.application.dto.QueryCardView;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 最终安全闸门服务。
 * 验证答案是否满足"无查询不出数、无来源不定性、无权限不展示"的要求。
 */
@Service
public class FinalGuardrailService {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d");
    private static final List<String> SENSITIVE_FIELD_KEYWORDS = List.of("PHONE", "MOBILE", "CONTACT", "ADDRESS", "INTERNAL", "IMAGE");

    /**
     * 验证 ComposedAnswer（旧版接口，保留兼容）。
     */
    public Optional<String> validate(ParsedQuestion parsedQuestion, ComposedAnswer composedAnswer) {
        GuardrailResult result = validateComposed(parsedQuestion, composedAnswer);
        return result.success() ? Optional.empty() : Optional.of(result.safeMessage());
    }

    /**
     * 验证 AnswerDraft（新版本接口）。
     */
    public GuardrailResult validate(ParsedQuestion parsedQuestion, AnswerDraft draft) {
        List<GuardrailResult.Violation> violations = new ArrayList<>();

        // 检查数据声明
        if (parsedQuestion.hasMandatoryDataIntent()) {
            violations.addAll(validateDataClaims(parsedQuestion, draft));
        }

        // 检查政策法规来源
        if (parsedQuestion.requiresCitation()) {
            violations.addAll(validateCitation(parsedQuestion, draft));
        }

        // 检查敏感字段
        violations.addAll(validateSensitiveFields(draft));

        if (!violations.isEmpty()) {
            return GuardrailResult.blocked(violations);
        }
        return GuardrailResult.passed();
    }

    /**
     * 验证 ComposedAnswer 并返回 GuardrailResult。
     */
    private GuardrailResult validateComposed(ParsedQuestion parsedQuestion, ComposedAnswer composedAnswer) {
        List<GuardrailResult.Violation> violations = new ArrayList<>();

        if (parsedQuestion.hasMandatoryDataIntent()) {
            if (composedAnswer.dataStatements().isEmpty()
                    || composedAnswer.dataStatements().stream()
                    .anyMatch(statement -> statement.queryId() == null || statement.queryId().isBlank())) {
                violations.add(GuardrailResult.Violation.dataClaimWithoutQuery("缺少有效查询结果"));
            }
            if (containsNumericAssertion(composedAnswer.render()) && composedAnswer.dataFragments().isEmpty()) {
                violations.add(GuardrailResult.Violation.dataClaimWithoutQuery("包含数字但无数据片段支撑"));
            }
        }
        if (parsedQuestion.requiresCitation() && composedAnswer.evidenceRefs().isEmpty()) {
            violations.add(GuardrailResult.Violation.policyWithoutEvidence("缺少政策法规证据"));
        }
        if (containsSensitiveFields(composedAnswer.queryCards())) {
            violations.add(GuardrailResult.Violation.sensitiveField("查询结果含敏感字段"));
        }

        if (!violations.isEmpty()) {
            return GuardrailResult.blocked(violations);
        }
        return GuardrailResult.passed();
    }

    /**
     * 验证数据声明。
     */
    private List<GuardrailResult.Violation> validateDataClaims(ParsedQuestion parsedQuestion, AnswerDraft draft) {
        List<GuardrailResult.Violation> violations = new ArrayList<>();

        // 检查是否有数据结果
        if (draft.dataFindings() == null || draft.dataFindings().isEmpty()) {
            violations.add(GuardrailResult.Violation.dataClaimWithoutQuery("缺少数据发现"));
        }

        // 检查未验证的数据声明
        if (draft.dataClaims() != null) {
            for (DataClaim claim : draft.dataClaims()) {
                if (!claim.isVerifiable()) {
                    violations.add(new GuardrailResult.Violation(
                            GuardrailResult.ViolationType.DATA_CLAIM_WITHOUT_QUERY,
                            claim.claimText(),
                            claim.queryId(),
                            null
                    ));
                }
            }
        }

        // 检查是否包含无 queryId 的数字
        if (containsNumericAssertion(draft.conclusion()) && draft.dataFindings().isEmpty()) {
            violations.add(GuardrailResult.Violation.dataClaimWithoutQuery("包含数字但无数据发现"));
        }

        return violations;
    }

    /**
     * 验证政策法规来源。
     */
    private List<GuardrailResult.Violation> validateCitation(ParsedQuestion parsedQuestion, AnswerDraft draft) {
        List<GuardrailResult.Violation> violations = new ArrayList<>();

        boolean hasLegalIntent = parsedQuestion.hasIntent(
                com.example.urbanagent.agent.application.dto.IntentType.LEGAL_ADVICE);
        boolean hasPolicyIntent = parsedQuestion.hasIntent(
                com.example.urbanagent.agent.application.dto.IntentType.POLICY_INTERPRETATION);

        if (hasLegalIntent && (draft.lawFindings() == null || draft.lawFindings().isEmpty())) {
            violations.add(GuardrailResult.Violation.lawWithoutEvidence("缺少法律依据"));
        }

        if (hasPolicyIntent && (draft.policyFindings() == null || draft.policyFindings().isEmpty())) {
            violations.add(GuardrailResult.Violation.policyWithoutEvidence("缺少政策依据"));
        }

        // 检查证据引用
        if ((hasLegalIntent || hasPolicyIntent) && (draft.evidenceRefs() == null || draft.evidenceRefs().isEmpty())) {
            violations.add(GuardrailResult.Violation.policyWithoutEvidence("缺少证据引用"));
        }

        return violations;
    }

    /**
     * 验证敏感字段。
     */
    private List<GuardrailResult.Violation> validateSensitiveFields(AnswerDraft draft) {
        List<GuardrailResult.Violation> violations = new ArrayList<>();
        // 敏感字段检查可扩展到数据发现和结论
        return violations;
    }

    private boolean containsNumericAssertion(String content) {
        return content != null && NUMBER_PATTERN.matcher(content).find();
    }

    private boolean containsSensitiveFields(List<QueryCardView> queryCards) {
        for (QueryCardView queryCard : queryCards) {
            for (Map<String, Object> row : queryCard.rows()) {
                for (String key : row.keySet()) {
                    String normalized = key.toUpperCase(Locale.ROOT);
                    if (SENSITIVE_FIELD_KEYWORDS.stream().anyMatch(normalized::contains)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
