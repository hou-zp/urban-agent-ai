package com.example.urbanagent.ai.application;

import java.util.List;
import java.util.stream.Stream;

public interface ModelProvider {

    String name();

    default String embeddingModelName() {
        return name();
    }

    String chat(List<String> history, String prompt);

    default String structuredChat(List<String> history, String prompt, String jsonSchema) {
        return chat(history, prompt + "\n\n请严格输出符合以下 JSON Schema 的 JSON，不要输出 Markdown：\n" + jsonSchema);
    }

    Stream<ModelChunk> streamChat(List<String> history, String prompt);

    default float[] embed(String content) {
        return new float[0];
    }
}
