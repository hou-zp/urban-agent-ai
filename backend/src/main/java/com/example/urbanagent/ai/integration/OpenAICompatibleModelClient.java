package com.example.urbanagent.ai.integration;

import com.example.urbanagent.ai.application.ModelProperties;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAICompatibleModelClient {

    private final RestClient.Builder restClientBuilder;
    private final ModelProperties properties;

    public OpenAICompatibleModelClient(RestClient.Builder restClientBuilder, ModelProperties properties) {
        this.restClientBuilder = restClientBuilder;
        this.properties = properties;
    }

    public String chat(List<String> history, String prompt) {
        ensureChatConfigured();
        OpenAIChatResponse response = post(
                properties.getBaseUrl(),
                properties.getChatPath(),
                properties.getApiKey(),
                buildChatBody(
                        buildMessages(history, prompt),
                        null
                ), OpenAIChatResponse.class);
        return extractChatContent(response);
    }

    public String structuredChat(List<String> history, String prompt, String jsonSchema) {
        ensureChatConfigured();
        if (jsonSchema == null || jsonSchema.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结构化输出 JSON Schema 不能为空");
        }
        String structuredPrompt = prompt
                + "\n\n请严格输出符合以下 JSON Schema 的 JSON，不要输出 Markdown，不要输出额外解释：\n"
                + jsonSchema;
        OpenAIChatResponse response = post(
                properties.getBaseUrl(),
                properties.getChatPath(),
                properties.getApiKey(),
                buildChatBody(
                        buildMessages(history, structuredPrompt),
                        Map.of("type", "json_object")
                ), OpenAIChatResponse.class);
        return extractChatContent(response);
    }

    public float[] embed(String content) {
        ensureEmbeddingConfigured();
        OpenAIEmbeddingResponse response = post(effectiveEmbeddingBaseUrl(), properties.getEmbeddingsPath(), effectiveEmbeddingApiKey(), Map.of(
                "model", properties.getEmbeddingModel(),
                "input", content == null ? "" : content
        ), OpenAIEmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "模型未返回有效向量");
        }
        List<Double> embedding = response.data().get(0).embedding();
        if (embedding == null || embedding.isEmpty()) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "模型向量为空");
        }
        float[] vector = new float[embedding.size()];
        for (int index = 0; index < embedding.size(); index++) {
            vector[index] = embedding.get(index).floatValue();
        }
        return vector;
    }

    private List<Map<String, String>> buildMessages(List<String> history, String prompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是城管业务智能助手，回答必须准确、简洁、可追溯。"));
        for (String item : history) {
            if (item != null && !item.isBlank()) {
                messages.add(Map.of("role", "user", "content", item));
            }
        }
        messages.add(Map.of("role", "user", "content", prompt));
        return messages;
    }

    private Map<String, Object> buildChatBody(List<Map<String, String>> messages, Map<String, String> responseFormat) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getChatModel());
        body.put("messages", messages);
        body.put("temperature", properties.getTemperature());
        body.put("stream", false);
        if (responseFormat != null && !responseFormat.isEmpty()) {
            body.put("response_format", responseFormat);
        }
        return body;
    }

    private String extractChatContent(OpenAIChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "模型未返回有效回答");
        }
        OpenAIMessage message = response.choices().get(0).message();
        if (message == null || message.content() == null || message.content().isBlank()) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "模型回答内容为空");
        }
        return message.content();
    }

    private <T> T post(String baseUrl, String path, String apiKey, Map<String, Object> body, Class<T> responseType) {
        ensureBaseConfigured(baseUrl, "模型服务地址未配置");
        try {
            RestClient.RequestBodySpec request = buildRestClient(baseUrl)
                    .post()
                    .uri(normalizePath(path));
            if (apiKey != null && !apiKey.isBlank()) {
                request.header("Authorization", "Bearer " + apiKey.trim());
            }
            return request
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "模型服务调用失败");
        }
    }

    private RestClient buildRestClient(String baseUrl) {
        RestClient.Builder builder = restClientBuilder.clone()
                .baseUrl(trimTrailingSlash(baseUrl));
        if (properties.getConnectTimeoutMs() > 0 || properties.getReadTimeoutMs() > 0) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            if (properties.getConnectTimeoutMs() > 0) {
                requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
            }
            if (properties.getReadTimeoutMs() > 0) {
                requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
            }
            builder.requestFactory(requestFactory);
        }
        return builder.build();
    }

    private void ensureChatConfigured() {
        ensureBaseConfigured(properties.getBaseUrl(), "模型服务地址未配置");
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "模型 API Key 未配置");
        }
        if (properties.getChatModel() == null || properties.getChatModel().isBlank()) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "chat model 未配置");
        }
    }

    private void ensureEmbeddingConfigured() {
        ensureBaseConfigured(effectiveEmbeddingBaseUrl(), "向量模型服务地址未配置");
        if (properties.getEmbeddingModel() == null || properties.getEmbeddingModel().isBlank()) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "embedding model 未配置");
        }
    }

    private void ensureBaseConfigured(String baseUrl, String message) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, message);
        }
    }

    private String effectiveEmbeddingBaseUrl() {
        return properties.getEmbeddingBaseUrl() == null || properties.getEmbeddingBaseUrl().isBlank()
                ? properties.getBaseUrl()
                : properties.getEmbeddingBaseUrl();
    }

    private String effectiveEmbeddingApiKey() {
        if (properties.getEmbeddingApiKey() != null && !properties.getEmbeddingApiKey().isBlank()) {
            return properties.getEmbeddingApiKey();
        }
        if (properties.getEmbeddingBaseUrl() != null && !properties.getEmbeddingBaseUrl().isBlank()) {
            return null;
        }
        return properties.getApiKey();
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private record OpenAIChatResponse(List<OpenAIChoice> choices, OpenAIUsage usage) {
    }

    private record OpenAIChoice(OpenAIMessage message, @JsonProperty("finish_reason") String finishReason) {
    }

    private record OpenAIMessage(String role, String content) {
    }

    private record OpenAIEmbeddingResponse(List<OpenAIEmbeddingData> data, OpenAIUsage usage) {
    }

    private record OpenAIEmbeddingData(Integer index, List<Double> embedding) {
    }

    private record OpenAIUsage(@JsonProperty("prompt_tokens") Integer promptTokens,
                               @JsonProperty("completion_tokens") Integer completionTokens,
                               @JsonProperty("total_tokens") Integer totalTokens) {
    }
}
