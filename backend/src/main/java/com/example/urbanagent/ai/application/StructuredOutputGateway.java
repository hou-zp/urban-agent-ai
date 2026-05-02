package com.example.urbanagent.ai.application;

import java.util.List;

public interface StructuredOutputGateway {

    StructuredOutputResult generate(StructuredOutputRequest request);

    default StructuredOutputResult generate(List<String> history, String prompt, String jsonSchema) {
        return generate(new StructuredOutputRequest(history, prompt, jsonSchema, 0));
    }

    record StructuredOutputRequest(List<String> history, String prompt, String jsonSchema, int maxRetries) {

        public StructuredOutputRequest {
            history = history == null ? List.of() : List.copyOf(history);
            prompt = prompt == null ? "" : prompt;
            jsonSchema = jsonSchema == null ? "" : jsonSchema.trim();
            maxRetries = Math.max(0, maxRetries);
        }
    }

    record StructuredOutputResult(String content, int attempts, boolean valid, String validationError) {

        public StructuredOutputResult {
            content = content == null ? "" : content;
            attempts = Math.max(1, attempts);
            validationError = validationError == null || validationError.isBlank() ? null : validationError;
        }
    }
}
