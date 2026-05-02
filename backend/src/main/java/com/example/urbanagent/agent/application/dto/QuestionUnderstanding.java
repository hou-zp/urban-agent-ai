package com.example.urbanagent.agent.application.dto;

import java.util.LinkedHashSet;
import java.util.List;

public record QuestionUnderstanding(
        List<QuestionType> questionTypes,
        QuestionType primaryQuestionType,
        List<UrbanScene> scenes,
        DataQueryIntent dataIntent,
        List<DataQueryIntent> dataIntents,
        List<KnowledgeIntent> knowledgeIntents,
        boolean needCitation,
        boolean needSuggestion,
        double confidence,
        UnderstandingSource source
) {

    public QuestionUnderstanding {
        questionTypes = questionTypes == null ? List.of() : questionTypes.stream()
                .filter(type -> type != null)
                .distinct()
                .toList();
        if (primaryQuestionType != null && !questionTypes.contains(primaryQuestionType)) {
            questionTypes = java.util.stream.Stream.concat(java.util.stream.Stream.of(primaryQuestionType), questionTypes.stream())
                    .distinct()
                    .toList();
        }
        primaryQuestionType = primaryQuestionType == null && !questionTypes.isEmpty() ? questionTypes.get(0) : primaryQuestionType;
        scenes = scenes == null ? List.of() : scenes.stream()
                .filter(scene -> scene != null)
                .distinct()
                .toList();
        dataIntents = dataIntents == null ? List.of() : dataIntents.stream()
                .filter(intent -> intent != null)
                .distinct()
                .toList();
        if (dataIntent == null && !dataIntents.isEmpty()) {
            dataIntent = dataIntents.get(0);
        }
        if (dataIntent != null && !dataIntents.contains(dataIntent)) {
            dataIntents = java.util.stream.Stream.concat(java.util.stream.Stream.of(dataIntent), dataIntents.stream())
                    .distinct()
                    .toList();
        }
        knowledgeIntents = knowledgeIntents == null ? List.of() : knowledgeIntents.stream()
                .filter(intent -> intent != null)
                .distinct()
                .toList();
        confidence = Math.max(0D, Math.min(0.99D, confidence));
        source = source == null ? UnderstandingSource.RULE_FALLBACK : source;
    }

    public QuestionUnderstanding(List<QuestionType> questionTypes,
                                 QuestionType primaryQuestionType,
                                 List<UrbanScene> scenes,
                                 DataQueryIntent dataIntent,
                                 List<DataQueryIntent> dataIntents,
                                 boolean needCitation,
                                 boolean needSuggestion,
                                 double confidence,
                                 UnderstandingSource source) {
        this(
                questionTypes,
                primaryQuestionType,
                scenes,
                dataIntent,
                dataIntents,
                List.of(),
                needCitation,
                needSuggestion,
                confidence,
                source
        );
    }

    public QuestionUnderstanding(List<QuestionType> questionTypes,
                                 QuestionType primaryQuestionType,
                                 List<UrbanScene> scenes,
                                 DataQueryIntent dataIntent,
                                 boolean needCitation,
                                 boolean needSuggestion,
                                 double confidence,
                                 UnderstandingSource source) {
        this(
                questionTypes,
                primaryQuestionType,
                scenes,
                dataIntent,
                dataIntent == null ? List.of() : List.of(dataIntent),
                List.of(),
                needCitation,
                needSuggestion,
                confidence,
                source
        );
    }

    public static QuestionUnderstanding fromParsedQuestion(String originalQuestion,
                                                           List<ParsedIntent> intents,
                                                           List<UrbanScene> scenes,
                                                           List<ExtractedSlot> slots,
                                                           double confidence) {
        List<QuestionType> questionTypes = intents == null ? List.of() : intents.stream()
                .map(ParsedIntent::intentType)
                .map(QuestionType::fromIntentType)
                .distinct()
                .toList();
        QuestionType primaryQuestionType = questionTypes.isEmpty() ? null : questionTypes.get(0);
        DataQueryIntent dataIntent = buildDataIntent(slots);
        boolean needCitation = intents != null && intents.stream()
                .map(ParsedIntent::intentType)
                .anyMatch(intentType -> intentType == IntentType.LEGAL_ADVICE
                        || intentType == IntentType.POLICY_INTERPRETATION
                        || intentType == IntentType.BUSINESS_CONSULTATION);
        boolean needSuggestion = intents != null && intents.stream()
                .map(ParsedIntent::intentType)
                .anyMatch(intentType -> intentType == IntentType.BUSINESS_CONSULTATION);
        return new QuestionUnderstanding(
                questionTypes,
                primaryQuestionType,
                scenes,
                dataIntent,
                dataIntent == null ? List.of() : List.of(dataIntent),
                List.of(),
                needCitation,
                needSuggestion,
                confidence,
                UnderstandingSource.RULE_FALLBACK
        );
    }

    private static DataQueryIntent buildDataIntent(List<ExtractedSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return null;
        }
        String metricCode = firstSlotValue(slots, SlotType.METRIC);
        AnalysisIntent analysisIntent = analysisIntent(slots);
        String timeExpression = firstSlotRawOrValue(slots, SlotType.TIME);
        String regionCode = firstSlotValue(slots, SlotType.REGION);
        if (metricCode.isBlank() && analysisIntent == AnalysisIntent.TOTAL && timeExpression.isBlank() && regionCode.isBlank()) {
            return null;
        }
        return new DataQueryIntent(metricCode, analysisIntent, List.of(), timeExpression, regionCode);
    }

    private static String firstSlotValue(List<ExtractedSlot> slots, SlotType slotType) {
        return slots.stream()
                .filter(slot -> slot.slotType() == slotType)
                .map(ExtractedSlot::normalizedValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("");
    }

    private static String firstSlotRawOrValue(List<ExtractedSlot> slots, SlotType slotType) {
        return slots.stream()
                .filter(slot -> slot.slotType() == slotType)
                .map(slot -> slot.rawValue() == null || slot.rawValue().isBlank() ? slot.normalizedValue() : slot.rawValue())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("");
    }

    private static AnalysisIntent analysisIntent(List<ExtractedSlot> slots) {
        LinkedHashSet<String> formats = new LinkedHashSet<>(slots.stream()
                .filter(slot -> slot.slotType() == SlotType.OUTPUT_FORMAT)
                .map(ExtractedSlot::normalizedValue)
                .filter(value -> value != null && !value.isBlank())
                .toList());
        if (formats.contains("ranking")) {
            return AnalysisIntent.RANKING;
        }
        if (formats.contains("distribution")) {
            return AnalysisIntent.DISTRIBUTION;
        }
        if (formats.contains("trend")) {
            return AnalysisIntent.TREND;
        }
        if (formats.contains("comparison")) {
            return AnalysisIntent.COMPARISON;
        }
        if (formats.contains("detail")) {
            return AnalysisIntent.DETAIL;
        }
        if (formats.contains("analysis")) {
            return AnalysisIntent.ANALYSIS;
        }
        return AnalysisIntent.TOTAL;
    }
}
