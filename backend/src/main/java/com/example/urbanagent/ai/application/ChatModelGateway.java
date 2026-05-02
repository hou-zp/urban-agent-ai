package com.example.urbanagent.ai.application;

import java.util.List;
import java.util.stream.Stream;

public interface ChatModelGateway {

    String name();

    default String modelName() {
        return name();
    }

    String chat(List<String> history, String prompt);

    Stream<ModelChunk> streamChat(List<String> history, String prompt);
}
