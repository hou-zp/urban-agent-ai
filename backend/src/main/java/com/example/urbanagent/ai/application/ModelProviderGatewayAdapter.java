package com.example.urbanagent.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

@Component("legacyModelGatewayAdapter")
public class ModelProviderGatewayAdapter implements ChatModelGateway, EmbeddingGateway, StructuredOutputGateway {

    private final ModelProvider modelProvider;
    private final ObjectMapper objectMapper;

    public ModelProviderGatewayAdapter(ModelProvider modelProvider, ObjectMapper objectMapper) {
        this.modelProvider = modelProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return modelProvider.name();
    }

    @Override
    public String modelName() {
        return modelProvider.name();
    }

    @Override
    public String chat(List<String> history, String prompt) {
        return modelProvider.chat(history, prompt);
    }

    @Override
    public Stream<ModelChunk> streamChat(List<String> history, String prompt) {
        return modelProvider.streamChat(history, prompt);
    }

    @Override
    public String embeddingModelName() {
        return modelProvider.embeddingModelName();
    }

    @Override
    public float[] embed(String content) {
        return modelProvider.embed(content);
    }

    @Override
    public StructuredOutputResult generate(StructuredOutputRequest request) {
        int totalAttempts = request.maxRetries() + 1;
        String lastContent = "";
        String lastValidationError = "模型未返回结构化内容";
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            lastContent = modelProvider.structuredChat(request.history(), request.prompt(), request.jsonSchema());
            lastValidationError = validateStructuredOutput(lastContent);
            if (lastValidationError == null) {
                return new StructuredOutputResult(lastContent, attempt, true, null);
            }
        }
        return new StructuredOutputResult(lastContent, totalAttempts, false, lastValidationError);
    }

    private String validateStructuredOutput(String content) {
        if (content == null || content.isBlank()) {
            return "模型未返回结构化内容";
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            if (!root.isContainerNode()) {
                return "结构化输出不是 JSON 对象或数组";
            }
            return null;
        } catch (JsonProcessingException ex) {
            return "模型未返回合法 JSON";
        }
    }
}
