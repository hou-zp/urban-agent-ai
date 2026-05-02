package com.example.urbanagent.common.error;

public enum ErrorCode {
    BAD_REQUEST(10000, "request is invalid"),
    USER_CONTEXT_INVALID(10001, "user context is invalid"),
    ACCESS_DENIED(10002, "access denied"),
    SESSION_NOT_FOUND(20001, "session not found"),
    KNOWLEDGE_DOCUMENT_NOT_FOUND(30001, "knowledge document not found"),
    KNOWLEDGE_DOCUMENT_STATUS_INVALID(30002, "knowledge document status is invalid"),
    KNOWLEDGE_DOCUMENT_PARSE_FAILED(30003, "knowledge document parse failed"),
    KNOWLEDGE_DOCUMENT_REJECTED(30004, "knowledge document rejected"),
    KNOWLEDGE_ATTACHMENT_NOT_FOUND(30005, "knowledge attachment not found"),
    KNOWLEDGE_ATTACHMENT_REJECTED(30006, "knowledge attachment rejected"),
    METRIC_NOT_FOUND(40001, "metric definition not found"),
    SQL_GENERATION_FAILED(40002, "failed to generate sql"),
    SQL_VALIDATION_FAILED(40003, "sql validation failed"),
    SQL_PERMISSION_DENIED(40004, "sql permission denied"),
    SQL_EXECUTION_FAILED(40005, "sql execution failed"),
    RATE_LIMITED(40006, "too many requests"),
    LEGAL_REVIEW_NOT_FOUND(50001, "legal review not found"),
    LEGAL_REVIEW_STATUS_INVALID(50002, "legal review status is invalid"),
    RUN_NOT_FOUND(20002, "run not found"),
    RUN_NOT_CANCELLABLE(20003, "run is not cancellable"),
    RUN_NOT_RESUMABLE(20004, "run is not resumable"),
    RUN_CANCELLED(20005, "run cancelled"),
    RUN_TIMEOUT(20006, "run timeout"),
    PLAN_NOT_FOUND(20007, "plan not found"),
    PLAN_STEP_NOT_FOUND(20008, "plan step not found"),
    MODEL_UNAVAILABLE(90001, "model provider is unavailable"),
    STREAM_FAILED(90002, "stream failed");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
