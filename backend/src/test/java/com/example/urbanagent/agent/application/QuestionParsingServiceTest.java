package com.example.urbanagent.agent.application;

import com.example.urbanagent.ai.application.StructuredOutputGateway;
import com.example.urbanagent.agent.application.dto.AnalysisIntent;
import com.example.urbanagent.agent.application.dto.IntentType;
import com.example.urbanagent.agent.application.dto.KnowledgeIntent;
import com.example.urbanagent.agent.application.dto.ParsedQuestion;
import com.example.urbanagent.agent.application.dto.SlotType;
import com.example.urbanagent.agent.application.dto.UnderstandingSource;
import com.example.urbanagent.agent.application.dto.UrbanScene;
import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionParsingServiceTest {

    private final QuestionParsingService questionParsingService = new QuestionParsingService(
            Clock.fixed(Instant.parse("2026-05-02T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
            new ObjectMapper(),
            null,
            null
    );

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void shouldDetectMultipleIntentsAndKeySlots() {
        ParsedQuestion parsedQuestion = questionParsingService.analyze("请根据法规说明本周柯桥区投诉数量排行，并给出处置建议");

        assertThat(parsedQuestion.hasIntent(IntentType.METRIC_QUERY)).isTrue();
        assertThat(parsedQuestion.hasIntent(IntentType.LEGAL_ADVICE)).isTrue();
        assertThat(parsedQuestion.hasIntent(IntentType.BUSINESS_CONSULTATION)).isTrue();
        assertThat(parsedQuestion.hasMandatoryDataIntent()).isTrue();
        assertThat(parsedQuestion.requiresCitation()).isTrue();
        assertThat(parsedQuestion.hasSlot(SlotType.TIME)).isTrue();
        assertThat(parsedQuestion.hasSlot(SlotType.REGION)).isTrue();
        assertThat(parsedQuestion.hasSlot(SlotType.METRIC)).isTrue();
        assertThat(parsedQuestion.slotsOf(SlotType.OUTPUT_FORMAT))
                .extracting(slot -> slot.normalizedValue())
                .contains("ranking");
    }

    @Test
    void shouldFallbackToCurrentUserRegionAndDetectScene() {
        UserContextHolder.set(new UserContext("demo-user", "OFFICER", "district-a"));

        ParsedQuestion parsedQuestion = questionParsingService.analyze("最近7天油烟异常商户点位状态");

        assertThat(parsedQuestion.hasIntent(IntentType.BUSINESS_DATA_QUERY)).isTrue();
        assertThat(parsedQuestion.scenes()).contains(UrbanScene.CATERING_OIL_FUME);
        assertThat(parsedQuestion.slotsOf(SlotType.REGION))
                .extracting(slot -> slot.normalizedValue())
                .contains("district-a");
        assertThat(parsedQuestion.slotsOf(SlotType.OBJECT))
                .extracting(slot -> slot.normalizedValue())
                .containsAnyOf("merchant", "point");
        assertThat(parsedQuestion.slotsOf(SlotType.STATUS))
                .extracting(slot -> slot.normalizedValue())
                .containsAnyOf("abnormal", "status");
    }

    @Test
    void shouldUseHighConfidenceModelUnderstandingForDistribution() {
        StructuredOutputGateway structuredOutputGateway = mock(StructuredOutputGateway.class);
        when(structuredOutputGateway.generate(any())).thenReturn(new StructuredOutputGateway.StructuredOutputResult("""
                {
                  "questionTypes": ["METRIC_QUERY"],
                  "primaryQuestionType": "METRIC_QUERY",
                  "scenes": ["STREET_ORDER"],
                  "dataIntent": {
                    "metricCode": "complaint_count",
                    "analysisIntent": "DISTRIBUTION",
                    "dimensions": ["street_name"],
                    "timeExpression": "本周",
                    "regionCode": "shaoxing-keqiao"
                  },
                  "needCitation": false,
                  "needSuggestion": false,
                  "confidence": 0.92
                }
                """, 1, true, null));
        QuestionParsingService enhancedService = new QuestionParsingService(
                Clock.fixed(Instant.parse("2026-05-02T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
                new ObjectMapper(),
                null,
                structuredOutputGateway
        );

        ParsedQuestion enhanced = enhancedService.analyze("本周柯桥区投诉数量分布");

        assertThat(enhanced.hasIntent(IntentType.METRIC_QUERY)).isTrue();
        assertThat(enhanced.scenes()).contains(UrbanScene.STREET_ORDER);
        assertThat(enhanced.analysisIntent()).contains(AnalysisIntent.DISTRIBUTION);
        assertThat(enhanced.dataIntent()).get()
                .satisfies(dataIntent -> assertThat(dataIntent.dimensions()).containsExactly("street_name"));
        assertThat(enhanced.slotsOf(SlotType.OUTPUT_FORMAT))
                .extracting(slot -> slot.normalizedValue())
                .contains("distribution");
        assertThat(enhanced.understanding().source()).isEqualTo(UnderstandingSource.MODEL);
        assertThat(enhanced.hasMandatoryDataIntent()).isTrue();
    }

    @Test
    void shouldMergeLowConfidenceModelUnderstandingWithRules() {
        StructuredOutputGateway structuredOutputGateway = mock(StructuredOutputGateway.class);
        when(structuredOutputGateway.generate(any())).thenReturn(new StructuredOutputGateway.StructuredOutputResult("""
                {
                  "questionTypes": ["METRIC_QUERY"],
                  "primaryQuestionType": "METRIC_QUERY",
                  "scenes": ["STREET_ORDER"],
                  "dataIntent": {
                    "metricCode": "complaint_count",
                    "analysisIntent": "DISTRIBUTION",
                    "dimensions": ["street_name"],
                    "timeExpression": "",
                    "regionCode": ""
                  },
                  "needCitation": false,
                  "needSuggestion": false,
                  "confidence": 0.62
                }
                """, 1, true, null));
        QuestionParsingService enhancedService = new QuestionParsingService(
                Clock.fixed(Instant.parse("2026-05-02T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
                new ObjectMapper(),
                null,
                structuredOutputGateway
        );

        ParsedQuestion enhanced = enhancedService.analyze("哪些街道投诉比较集中");

        assertThat(enhanced.hasIntent(IntentType.METRIC_QUERY)).isTrue();
        assertThat(enhanced.analysisIntent()).contains(AnalysisIntent.DISTRIBUTION);
        assertThat(enhanced.dataIntent()).get()
                .satisfies(dataIntent -> assertThat(dataIntent.dimensions()).containsExactly("street_name"));
        assertThat(enhanced.understanding().source()).isEqualTo(UnderstandingSource.MODEL_WITH_RULE_GUARDRAIL);
    }

    @Test
    void shouldRecognizeMixedMetricLegalAndSuggestionIntentFromModel() {
        StructuredOutputGateway structuredOutputGateway = mock(StructuredOutputGateway.class);
        when(structuredOutputGateway.generate(any())).thenReturn(new StructuredOutputGateway.StructuredOutputResult("""
                {
                  "questionTypes": ["METRIC_QUERY", "LEGAL_ADVICE", "BUSINESS_CONSULTATION"],
                  "primaryQuestionType": "METRIC_QUERY",
                  "scenes": ["STREET_ORDER"],
                  "dataIntent": {
                    "metricCode": "complaint_count",
                    "analysisIntent": "RANKING",
                    "dimensions": ["street_name"],
                    "timeExpression": "本周",
                    "regionCode": "shaoxing-keqiao"
                  },
                  "needCitation": true,
                  "needSuggestion": true,
                  "confidence": 0.9
                }
                """, 1, true, null));
        QuestionParsingService enhancedService = new QuestionParsingService(
                Clock.fixed(Instant.parse("2026-05-02T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
                new ObjectMapper(),
                null,
                structuredOutputGateway
        );

        ParsedQuestion parsedQuestion = enhancedService.analyze("请根据法规说明本周投诉排行并给建议");

        assertThat(parsedQuestion.hasIntent(IntentType.METRIC_QUERY)).isTrue();
        assertThat(parsedQuestion.hasIntent(IntentType.LEGAL_ADVICE)).isTrue();
        assertThat(parsedQuestion.hasIntent(IntentType.BUSINESS_CONSULTATION)).isTrue();
        assertThat(parsedQuestion.requiresCitation()).isTrue();
        assertThat(parsedQuestion.analysisIntent()).contains(AnalysisIntent.RANKING);
    }

    @Test
    void shouldRecognizeOilFumeThresholdAsKnowledgeIntentWithoutTrendMetric() {
        StructuredOutputGateway structuredOutputGateway = mock(StructuredOutputGateway.class);
        when(structuredOutputGateway.generate(any())).thenReturn(new StructuredOutputGateway.StructuredOutputResult("""
                {
                  "questionTypes": ["METRIC_QUERY", "POLICY_INTERPRETATION"],
                  "primaryQuestionType": "METRIC_QUERY",
                  "scenes": ["CATERING_OIL_FUME"],
                  "dataIntent": {
                    "metricCode": "oil_fume_unclosed_warning_count",
                    "analysisIntent": "TOTAL",
                    "dimensions": [],
                    "timeExpression": "",
                    "regionCode": "shaoxing-keqiao"
                  },
                  "dataIntents": [
                    {
                      "metricCode": "oil_fume_unclosed_warning_count",
                      "analysisIntent": "TOTAL",
                      "dimensions": [],
                      "timeExpression": "",
                      "regionCode": "shaoxing-keqiao"
                    }
                  ],
                  "knowledgeIntents": ["OIL_FUME_THRESHOLD", "OIL_FUME_THRESHOLD_CHANGE"],
                  "needCitation": true,
                  "needSuggestion": false,
                  "confidence": 0.91
                }
                """, 1, true, null));
        QuestionParsingService enhancedService = new QuestionParsingService(
                Clock.fixed(Instant.parse("2026-05-02T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
                new ObjectMapper(),
                null,
                structuredOutputGateway
        );

        ParsedQuestion parsedQuestion = enhancedService.analyze("请问柯桥区当前的油烟浓度超标阀值是多少，与以前相比有什么变化，当前还有多少油烟浓度超标的预警未闭环");

        assertThat(parsedQuestion.requiresCitation()).isTrue();
        assertThat(parsedQuestion.knowledgeIntents())
                .containsExactly(KnowledgeIntent.OIL_FUME_THRESHOLD, KnowledgeIntent.OIL_FUME_THRESHOLD_CHANGE);
        assertThat(parsedQuestion.dataIntents())
                .hasSize(1)
                .first()
                .satisfies(dataIntent -> {
                    assertThat(dataIntent.metricCode()).isEqualTo("oil_fume_unclosed_warning_count");
                    assertThat(dataIntent.analysisIntent()).isEqualTo(AnalysisIntent.TOTAL);
                });
    }

    @Test
    void shouldFallbackToRuleParsingWhenStructuredOutputRemainsInvalid() {
        StructuredOutputGateway structuredOutputGateway = mock(StructuredOutputGateway.class);
        when(structuredOutputGateway.generate(any())).thenReturn(
                new StructuredOutputGateway.StructuredOutputResult("{invalid-json", 2, false, "模型未返回合法 JSON")
        );
        QuestionParsingService enhancedService = new QuestionParsingService(
                Clock.fixed(Instant.parse("2026-05-02T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
                new ObjectMapper(),
                null,
                structuredOutputGateway
        );

        ParsedQuestion baseline = questionParsingService.analyze("最近7天油烟异常商户点位状态");
        ParsedQuestion fallback = enhancedService.analyze("最近7天油烟异常商户点位状态");

        assertThat(fallback).isEqualTo(baseline);
    }
}
