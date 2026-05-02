package com.example.urbanagent;

import com.example.urbanagent.ai.application.MockModelProvider;
import com.example.urbanagent.ai.application.ModelCallRecordService;
import com.example.urbanagent.ai.application.ModelProperties;
import com.example.urbanagent.ai.application.RoutingModelProvider;
import com.example.urbanagent.ai.integration.OpenAICompatibleModelClient;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RoutingModelProviderTest {

    private static final String ANSWER_SCHEMA = """
            {
              "type": "object",
              "required": ["answer", "confidence"],
              "properties": {
                "answer": { "type": "string" },
                "confidence": { "type": "number" },
                "citations": { "type": "array" },
                "warnings": { "type": "array" }
              }
            }
            """;

    @Test
    void shouldReturnUnavailableWhenOpenAICompatibleProviderUnavailable() {
        ModelProperties properties = openAICompatibleProperties();
        OpenAICompatibleModelClient client = mock(OpenAICompatibleModelClient.class);
        ModelCallRecordService recorder = mock(ModelCallRecordService.class);
        when(client.chat(anyList(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "模型服务不可用"));

        RoutingModelProvider provider = new RoutingModelProvider(properties, new MockModelProvider(), client, recorder);

        assertThatThrownBy(() -> provider.chat(List.of(), "请介绍系统能力"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("大模型服务暂不可用");

        verify(recorder).recordFailure(
                org.mockito.ArgumentMatchers.eq("openai-compatible"),
                org.mockito.ArgumentMatchers.eq("test-chat-model"),
                org.mockito.ArgumentMatchers.eq("CHAT"),
                anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(BusinessException.class)
        );
    }

    @Test
    void shouldReturnUnavailableForStructuredChatWhenOpenAICompatibleProviderUnavailable() {
        ModelProperties properties = openAICompatibleProperties();
        OpenAICompatibleModelClient client = mock(OpenAICompatibleModelClient.class);
        ModelCallRecordService recorder = mock(ModelCallRecordService.class);
        when(client.structuredChat(anyList(), anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "模型服务不可用"));

        RoutingModelProvider provider = new RoutingModelProvider(properties, new MockModelProvider(), client, recorder);

        assertThatThrownBy(() -> provider.structuredChat(List.of("历史问题"), "请输出结构化答复", ANSWER_SCHEMA))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("大模型服务暂不可用");

        verify(recorder).recordFailure(
                org.mockito.ArgumentMatchers.eq("openai-compatible"),
                org.mockito.ArgumentMatchers.eq("test-chat-model"),
                org.mockito.ArgumentMatchers.eq("STRUCTURED_CHAT"),
                anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(BusinessException.class)
        );
    }

    @Test
    void shouldPropagateNonUnavailableBusinessException() {
        ModelProperties properties = openAICompatibleProperties();
        OpenAICompatibleModelClient client = mock(OpenAICompatibleModelClient.class);
        ModelCallRecordService recorder = mock(ModelCallRecordService.class);
        when(client.chat(anyList(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.BAD_REQUEST, "请求无效"));

        RoutingModelProvider provider = new RoutingModelProvider(properties, new MockModelProvider(), client, recorder);

        assertThatThrownBy(() -> provider.chat(List.of(), "请介绍系统能力"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请求无效");
        verify(recorder).recordFailure(
                org.mockito.ArgumentMatchers.eq("openai-compatible"),
                org.mockito.ArgumentMatchers.eq("test-chat-model"),
                org.mockito.ArgumentMatchers.eq("CHAT"),
                anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(BusinessException.class)
        );
    }

    @Test
    void shouldUseMockProviderWhenConfiguredProviderIsMock() {
        ModelProperties properties = new ModelProperties();
        properties.setProvider("mock");
        OpenAICompatibleModelClient client = mock(OpenAICompatibleModelClient.class);
        ModelCallRecordService recorder = mock(ModelCallRecordService.class);

        RoutingModelProvider provider = new RoutingModelProvider(properties, new MockModelProvider(), client, recorder);

        String response = provider.chat(List.of(), "请介绍系统能力");

        assertThat(response).contains("当前平台支持");
        verifyNoInteractions(client);
        verify(recorder).recordSuccess(
                org.mockito.ArgumentMatchers.eq("mock"),
                org.mockito.ArgumentMatchers.eq("mock-model"),
                org.mockito.ArgumentMatchers.eq("CHAT"),
                anyString(),
                anyString(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    void shouldUseMockProviderForStructuredChatWhenConfiguredProviderIsMock() throws Exception {
        ModelProperties properties = new ModelProperties();
        properties.setProvider("mock");
        OpenAICompatibleModelClient client = mock(OpenAICompatibleModelClient.class);
        ModelCallRecordService recorder = mock(ModelCallRecordService.class);

        RoutingModelProvider provider = new RoutingModelProvider(properties, new MockModelProvider(), client, recorder);

        String response = provider.structuredChat(List.of(), "请介绍系统能力", ANSWER_SCHEMA);

        assertThat(new ObjectMapper().readTree(response).get("confidence").asDouble()).isGreaterThan(0);
        verifyNoInteractions(client);
        verify(recorder).recordSuccess(
                org.mockito.ArgumentMatchers.eq("mock"),
                org.mockito.ArgumentMatchers.eq("mock-model"),
                org.mockito.ArgumentMatchers.eq("STRUCTURED_CHAT"),
                anyString(),
                anyString(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    private ModelProperties openAICompatibleProperties() {
        ModelProperties properties = new ModelProperties();
        properties.setProvider("openai-compatible");
        properties.setApiKey("test-key");
        properties.setBaseUrl("http://localhost:8089");
        properties.setChatModel("test-chat-model");
        properties.setEmbeddingModel("test-embedding-model");
        return properties;
    }
}
