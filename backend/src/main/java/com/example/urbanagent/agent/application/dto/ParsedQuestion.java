package com.example.urbanagent.agent.application.dto;

import com.example.urbanagent.knowledge.domain.KnowledgeCategory;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public record ParsedQuestion(
        String originalQuestion,
        List<ParsedIntent> intents,
        List<UrbanScene> scenes,
        List<ExtractedSlot> slots,
        double confidence,
        QuestionUnderstanding understanding
) {

    public ParsedQuestion(String originalQuestion,
                          List<ParsedIntent> intents,
                          List<UrbanScene> scenes,
                          List<ExtractedSlot> slots,
                          double confidence) {
        this(
                originalQuestion,
                intents,
                scenes,
                slots,
                confidence,
                QuestionUnderstanding.fromParsedQuestion(originalQuestion, intents, scenes, slots, confidence)
        );
    }

    public ParsedQuestion {
        originalQuestion = originalQuestion == null ? "" : originalQuestion;
        intents = intents == null ? List.of() : List.copyOf(intents);
        scenes = scenes == null ? List.of() : List.copyOf(scenes);
        slots = slots == null ? List.of() : List.copyOf(slots);
        confidence = Math.max(0D, Math.min(0.99D, confidence));
        if (understanding == null) {
            understanding = QuestionUnderstanding.fromParsedQuestion(originalQuestion, intents, scenes, slots, confidence);
        }
    }

    public boolean hasIntent(IntentType intentType) {
        return intents.stream().anyMatch(intent -> intent.intentType() == intentType);
    }

    public boolean hasAnyIntent(IntentType... intentTypes) {
        List<IntentType> expected = Arrays.asList(intentTypes);
        return intents.stream().anyMatch(intent -> expected.contains(intent.intentType()));
    }

    public boolean hasMandatoryDataIntent() {
        return intents.stream()
                .anyMatch(intent -> intent.mandatory()
                        && (intent.intentType() == IntentType.METRIC_QUERY
                        || intent.intentType() == IntentType.BUSINESS_DATA_QUERY));
    }

    public boolean requiresCitation() {
        if (understanding != null && understanding.needCitation()) {
            return true;
        }
        return hasAnyIntent(
                IntentType.LEGAL_ADVICE,
                IntentType.POLICY_INTERPRETATION,
                IntentType.BUSINESS_CONSULTATION
        );
    }

    public Optional<IntentType> primaryIntent() {
        return intents.stream()
                .sorted((left, right) -> {
                    int priorityDiff = Integer.compare(priority(right), priority(left));
                    if (priorityDiff != 0) {
                        return priorityDiff;
                    }
                    return Double.compare(right.confidence(), left.confidence());
                })
                .map(ParsedIntent::intentType)
                .findFirst();
    }

    public boolean hasSlot(SlotType slotType) {
        return slots.stream().anyMatch(slot -> slot.slotType() == slotType);
    }

    public List<ExtractedSlot> slotsOf(SlotType slotType) {
        return slots.stream()
                .filter(slot -> slot.slotType() == slotType)
                .toList();
    }

    public Optional<DataQueryIntent> dataIntent() {
        return Optional.ofNullable(understanding)
                .map(QuestionUnderstanding::dataIntent);
    }

    public List<DataQueryIntent> dataIntents() {
        if (understanding == null) {
            return dataIntent().stream().toList();
        }
        List<DataQueryIntent> dataIntents = understanding.dataIntents();
        if (dataIntents != null && !dataIntents.isEmpty()) {
            return dataIntents;
        }
        return dataIntent().stream().toList();
    }

    public List<KnowledgeIntent> knowledgeIntents() {
        return understanding == null ? List.of() : understanding.knowledgeIntents();
    }

    public Optional<AnalysisIntent> analysisIntent() {
        return dataIntent().map(DataQueryIntent::analysisIntent);
    }

    public Optional<KnowledgeCategory> preferredKnowledgeCategory() {
        if (hasIntent(IntentType.LEGAL_ADVICE)) {
            return Optional.of(KnowledgeCategory.LAW);
        }
        if (hasIntent(IntentType.POLICY_INTERPRETATION)) {
            return Optional.of(KnowledgeCategory.POLICY);
        }
        if (hasIntent(IntentType.BUSINESS_CONSULTATION)) {
            return Optional.of(KnowledgeCategory.BUSINESS);
        }
        return Optional.empty();
    }

    private int priority(ParsedIntent intent) {
        if (intent.mandatory()) {
            return 100;
        }
        return switch (intent.intentType()) {
            case LEGAL_ADVICE -> 90;
            case POLICY_INTERPRETATION -> 80;
            case BUSINESS_CONSULTATION -> 70;
            case BUSINESS_DATA_QUERY -> 60;
            case METRIC_QUERY -> 50;
        };
    }
}
