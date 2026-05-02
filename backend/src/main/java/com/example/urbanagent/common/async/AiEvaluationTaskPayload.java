package com.example.urbanagent.common.async;

public record AiEvaluationTaskPayload(String evaluationSet,
                                      String scenario,
                                      String runId,
                                      Integer sampleSize,
                                      String triggerReason) implements AsyncTaskPayload {
}
