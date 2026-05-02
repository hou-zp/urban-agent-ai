package com.example.urbanagent.ai.application;

public interface EmbeddingGateway {

    String embeddingModelName();

    float[] embed(String content);
}
