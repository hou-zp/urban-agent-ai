package com.example.urbanagent.agent.application.dto;

public enum QuestionType {
    BUSINESS_CONSULTATION,
    POLICY_INTERPRETATION,
    LEGAL_ADVICE,
    METRIC_QUERY,
    BUSINESS_DATA_QUERY;

    public IntentType toIntentType() {
        return IntentType.valueOf(name());
    }

    public static QuestionType fromIntentType(IntentType intentType) {
        return QuestionType.valueOf(intentType.name());
    }
}
