package com.example.urbanagent.ai.application;

import com.example.urbanagent.ai.integration.OpenAICompatibleModelClient;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Primary
@Component
public class RoutingModelProvider implements ModelProvider {

    private static final String MODEL_UNAVAILABLE_MESSAGE = "大模型服务暂不可用，请稍后再试。";

    private final ModelProperties properties;
    private final MockModelProvider mockModelProvider;
    private final OpenAICompatibleModelClient openAICompatibleModelClient;
    private final ModelCallRecordService modelCallRecordService;

    public RoutingModelProvider(ModelProperties properties,
                                MockModelProvider mockModelProvider,
                                OpenAICompatibleModelClient openAICompatibleModelClient,
                                ModelCallRecordService modelCallRecordService) {
        this.properties = properties;
        this.mockModelProvider = mockModelProvider;
        this.openAICompatibleModelClient = openAICompatibleModelClient;
        this.modelCallRecordService = modelCallRecordService;
    }

    @Override
    public String name() {
        if (useOpenAICompatible()) {
            return "openai-compatible:" + properties.getChatModel();
        }
        return mockModelProvider.name();
    }

    @Override
    public String embeddingModelName() {
        if (!useOpenAICompatible()) {
            return mockModelProvider.embeddingModelName();
        }
        String embeddingModel = properties.getEmbeddingModel();
        return embeddingModel == null || embeddingModel.isBlank() ? name() : embeddingModel;
    }

    @Override
    public String chat(List<String> history, String prompt) {
        if (!useOpenAICompatible()) {
            return callMockChat(history, prompt);
        }
        long startedAt = System.nanoTime();
        try {
            String response = openAICompatibleModelClient.chat(history, prompt);
            modelCallRecordService.recordSuccess(
                    "openai-compatible",
                    properties.getChatModel(),
                    "CHAT",
                    buildPromptForAudit(history, prompt),
                    response,
                    elapsedMs(startedAt)
            );
            return response;
        } catch (BusinessException ex) {
            modelCallRecordService.recordFailure(
                    "openai-compatible",
                    properties.getChatModel(),
                    "CHAT",
                    buildPromptForAudit(history, prompt),
                    elapsedMs(startedAt),
                    ex
            );
            throw normalizeModelException(ex);
        }
    }

    @Override
    public String structuredChat(List<String> history, String prompt, String jsonSchema) {
        if (!useOpenAICompatible()) {
            return callMockStructuredChat(history, prompt, jsonSchema);
        }
        long startedAt = System.nanoTime();
        try {
            String response = openAICompatibleModelClient.structuredChat(history, prompt, jsonSchema);
            modelCallRecordService.recordSuccess(
                    "openai-compatible",
                    properties.getChatModel(),
                    "STRUCTURED_CHAT",
                    buildStructuredPromptForAudit(history, prompt, jsonSchema),
                    response,
                    elapsedMs(startedAt)
            );
            return response;
        } catch (BusinessException ex) {
            modelCallRecordService.recordFailure(
                    "openai-compatible",
                    properties.getChatModel(),
                    "STRUCTURED_CHAT",
                    buildStructuredPromptForAudit(history, prompt, jsonSchema),
                    elapsedMs(startedAt),
                    ex
            );
            throw normalizeModelException(ex);
        }
    }

    @Override
    public Stream<ModelChunk> streamChat(List<String> history, String prompt) {
        String response = chat(history, prompt);
        String[] tokens = response.split(" ");
        java.util.ArrayList<ModelChunk> chunks = new java.util.ArrayList<>();
        for (int index = 0; index < tokens.length; index++) {
            chunks.add(new ModelChunk(tokens[index] + (index == tokens.length - 1 ? "" : " "), false));
        }
        chunks.add(new ModelChunk("", true));
        return chunks.stream();
    }

    @Override
    public float[] embed(String content) {
        if (!useOpenAICompatible()) {
            return callMockEmbed(content);
        }
        long startedAt = System.nanoTime();
        try {
            float[] vector = openAICompatibleModelClient.embed(content);
            modelCallRecordService.recordSuccess(
                    "openai-compatible",
                    properties.getEmbeddingModel(),
                    "EMBED",
                    content,
                    "vector:" + vector.length,
                    elapsedMs(startedAt)
            );
            return vector;
        } catch (BusinessException ex) {
            modelCallRecordService.recordFailure(
                    "openai-compatible",
                    properties.getEmbeddingModel(),
                    "EMBED",
                    content,
                    elapsedMs(startedAt),
                    ex
            );
            throw normalizeModelException(ex);
        }
    }

    private String callMockChat(List<String> history, String prompt) {
        long startedAt = System.nanoTime();
        String response = mockModelProvider.chat(history, prompt);
        modelCallRecordService.recordSuccess(
                "mock",
                mockModelProvider.name(),
                "CHAT",
                buildPromptForAudit(history, prompt),
                response,
                elapsedMs(startedAt)
        );
        return response;
    }

    private String callMockStructuredChat(List<String> history, String prompt, String jsonSchema) {
        long startedAt = System.nanoTime();
        String response = mockModelProvider.structuredChat(history, prompt, jsonSchema);
        modelCallRecordService.recordSuccess(
                "mock",
                mockModelProvider.name(),
                "STRUCTURED_CHAT",
                buildStructuredPromptForAudit(history, prompt, jsonSchema),
                response,
                elapsedMs(startedAt)
        );
        return response;
    }

    private float[] callMockEmbed(String content) {
        long startedAt = System.nanoTime();
        float[] vector = mockModelProvider.embed(content);
        modelCallRecordService.recordSuccess(
                "mock",
                mockModelProvider.name(),
                "EMBED",
                content,
                "vector:" + vector.length,
                elapsedMs(startedAt)
        );
        return vector;
    }

    private boolean useOpenAICompatible() {
        String provider = properties.getProvider();
        return provider != null && "openai-compatible".equals(provider.trim().toLowerCase(Locale.ROOT));
    }

    private String buildPromptForAudit(List<String> history, String prompt) {
        if (history == null || history.isEmpty()) {
            return prompt;
        }
        return String.join("\n", history) + "\n" + prompt;
    }

    private String buildStructuredPromptForAudit(List<String> history, String prompt, String jsonSchema) {
        return buildPromptForAudit(history, prompt) + "\nJSON_SCHEMA:\n" + (jsonSchema == null ? "" : jsonSchema);
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private BusinessException modelUnavailable() {
        return new BusinessException(ErrorCode.MODEL_UNAVAILABLE, MODEL_UNAVAILABLE_MESSAGE);
    }

    private BusinessException normalizeModelException(BusinessException ex) {
        if (ex.errorCode() == ErrorCode.MODEL_UNAVAILABLE) {
            return modelUnavailable();
        }
        return ex;
    }
}
