package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.AnswerDraft;
import com.example.urbanagent.agent.application.dto.DataClaim;
import com.example.urbanagent.agent.application.dto.GuardrailResult;
import com.example.urbanagent.agent.application.dto.IntentType;
import com.example.urbanagent.agent.application.dto.ParsedIntent;
import com.example.urbanagent.agent.application.dto.ParsedQuestion;
import com.example.urbanagent.agent.application.dto.QuestionUnderstanding;
import com.example.urbanagent.agent.application.dto.UrbanScene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FinalGuardrailServiceTest {

    private FinalGuardrailService guardrailService;

    @BeforeEach
    void setUp() {
        guardrailService = new FinalGuardrailService();
    }

    @Test
    void shouldPassWhenDataHasQueryId() {
        ParsedQuestion question = createQuestionWithDataIntent();
        AnswerDraft draft = AnswerDraft.builder()
                .conclusion("A区4月油烟投诉 87 件")
                .dataFindings(List.of("4月投诉量 87 件（查询编号：Q-001）"))
                .dataClaims(List.of(
                        new DataClaim("87 件", DataClaim.ClaimType.COUNT, "Q-001", true)
                ))
                .build();

        GuardrailResult result = guardrailService.validate(question, draft);
        assertTrue(result.success());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void shouldBlockWhenDataClaimWithoutQueryId() {
        ParsedQuestion question = createQuestionWithDataIntent();
        AnswerDraft draft = AnswerDraft.builder()
                .conclusion("A区4月油烟投诉上升")
                .dataFindings(List.of())  // 无数据发现
                .dataClaims(List.of(
                        new DataClaim("上升 15%", DataClaim.ClaimType.TREND, null, false)  // 无 queryId
                ))
                .build();

        GuardrailResult result = guardrailService.validate(question, draft);
        assertFalse(result.success());
        assertFalse(result.violations().isEmpty());
        assertTrue(result.safeMessage().contains("数据性结论"));
    }

    @Test
    void shouldBlockWhenLawWithoutEvidence() {
        ParsedQuestion question = createQuestionWithLegalIntent();
        AnswerDraft draft = AnswerDraft.builder()
                .conclusion("根据法规处理")
                .lawFindings(List.of())  // 无法规依据
                .evidenceRefs(List.of()) // 无证据引用
                .build();

        GuardrailResult result = guardrailService.validate(question, draft);
        assertFalse(result.success());
        assertTrue(result.violations().stream()
                .anyMatch(v -> v.type() == GuardrailResult.ViolationType.LAW_WITHOUT_EVIDENCE));
    }

    @Test
    void shouldBlockWhenPolicyWithoutEvidence() {
        ParsedQuestion question = createQuestionWithPolicyIntent();
        AnswerDraft draft = AnswerDraft.builder()
                .conclusion("根据政策执行")
                .policyFindings(List.of())  // 无政策依据
                .evidenceRefs(List.of())
                .build();

        GuardrailResult result = guardrailService.validate(question, draft);
        assertFalse(result.success());
        assertTrue(result.violations().stream()
                .anyMatch(v -> v.type() == GuardrailResult.ViolationType.POLICY_WITHOUT_EVIDENCE));
    }

    @Test
    void shouldPassWhenNoDataIntent() {
        // 无数据意图的问题应该直接通过
        ParsedQuestion question = new ParsedQuestion(
                "如何处理乱堆物料？",
                List.of(new ParsedIntent(IntentType.BUSINESS_CONSULTATION, false, "流程咨询", 0.8)),
                List.of(UrbanScene.MESSY_STACKING),
                List.of(),
                0.8
        );
        AnswerDraft draft = AnswerDraft.builder()
                .conclusion("请按流程处置")
                .suggestions(List.of("1. 核查现场", "2. 拍照留证"))
                .build();

        GuardrailResult result = guardrailService.validate(question, draft);
        assertTrue(result.success());
    }

    @Test
    void shouldGenerateSafeMessageBasedOnViolationType() {
        ParsedQuestion question = createQuestionWithDataIntent();
        AnswerDraft draft = AnswerDraft.builder()
                .conclusion("测试结论")
                .dataFindings(List.of())
                .build();

        GuardrailResult result = guardrailService.validate(question, draft);
        assertNotNull(result.safeMessage());
        assertFalse(result.safeMessage().isBlank());
    }

    private ParsedQuestion createQuestionWithDataIntent() {
        return new ParsedQuestion(
                "A区4月油烟投诉多少件？",
                List.of(new ParsedIntent(IntentType.METRIC_QUERY, true, "指标查询", 0.95)),
                List.of(UrbanScene.CATERING_OIL_FUME),
                List.of(),
                0.95
        );
    }

    private ParsedQuestion createQuestionWithLegalIntent() {
        return new ParsedQuestion(
                "餐饮油烟怎么处罚？",
                List.of(new ParsedIntent(IntentType.LEGAL_ADVICE, false, "法规咨询", 0.9)),
                List.of(UrbanScene.CATERING_OIL_FUME),
                List.of(),
                0.9
        );
    }

    private ParsedQuestion createQuestionWithPolicyIntent() {
        return new ParsedQuestion(
                "油烟专项治理有什么要求？",
                List.of(new ParsedIntent(IntentType.POLICY_INTERPRETATION, false, "政策解读", 0.85)),
                List.of(UrbanScene.CATERING_OIL_FUME),
                List.of(),
                0.85
        );
    }
}