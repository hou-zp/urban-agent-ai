package com.example.urbanagent;

import com.example.urbanagent.ai.application.ModelCallRecordService;
import com.example.urbanagent.ai.application.ModelProperties;
import com.example.urbanagent.ai.application.SpringAiStructuredOutputGateway;
import com.example.urbanagent.ai.application.StructuredOutputGateway;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiStructuredOutputGatewayTest {

    private static final String PARSED_QUESTION_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "required": ["originalQuestion", "intents", "scenes", "slots", "confidence"],
              "properties": {
                "originalQuestion": { "type": "string" },
                "intents": { "type": "array" },
                "scenes": { "type": "array" },
                "slots": { "type": "array" },
                "confidence": { "type": "number" }
              },
              "additionalProperties": false
            }
            """;

    @Test
    void shouldReturnValidParsedQuestionJson() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ModelCallRecordService recorder = mock(ModelCallRecordService.class);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("""
                {
                  "originalQuestion": "请分析本周油烟投诉情况",
                  "intents": [],
                  "scenes": [],
                  "slots": [],
                  "confidence": 0.91
                }
                """);

        SpringAiStructuredOutputGateway gateway = new SpringAiStructuredOutputGateway(
                chatClient,
                configuredProperties(),
                recorder,
                new ObjectMapper()
        );

        StructuredOutputGateway.StructuredOutputResult result = gateway.generate(
                new StructuredOutputGateway.StructuredOutputRequest(
                        java.util.List.of("历史问题"),
                        "请输出 ParsedQuestion JSON",
                        PARSED_QUESTION_SCHEMA,
                        0
                )
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.validationError()).isNull();
        assertThat(result.attempts()).isEqualTo(1);
        verify(recorder).recordSuccess(
                eq("spring-ai-openai"),
                eq("spring-ai-chat-model"),
                eq("STRUCTURED_CHAT"),
                anyString(),
                anyString(),
                anyLong()
        );
    }

    @Test
    void shouldReturnValidationErrorWhenJsonDoesNotMatchSchema() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ModelCallRecordService recorder = mock(ModelCallRecordService.class);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("""
                {
                  "originalQuestion": "请分析本周油烟投诉情况",
                  "intents": [],
                  "scenes": [],
                  "slots": []
                }
                """);

        SpringAiStructuredOutputGateway gateway = new SpringAiStructuredOutputGateway(
                chatClient,
                configuredProperties(),
                recorder,
                new ObjectMapper()
        );

        StructuredOutputGateway.StructuredOutputResult result = gateway.generate(
                new StructuredOutputGateway.StructuredOutputRequest(
                        java.util.List.of(),
                        "请输出 ParsedQuestion JSON",
                        PARSED_QUESTION_SCHEMA,
                        0
                )
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.validationError()).contains("confidence");
    }

    @Test
    void shouldRetryWhenFirstStructuredOutputIsInvalidJson() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ModelCallRecordService recorder = mock(ModelCallRecordService.class);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content())
                .thenReturn("{invalid-json")
                .thenReturn("""
                        {
                          "originalQuestion": "请分析本周油烟投诉情况",
                          "intents": [],
                          "scenes": [],
                          "slots": [],
                          "confidence": 0.91
                        }
                        """);

        SpringAiStructuredOutputGateway gateway = new SpringAiStructuredOutputGateway(
                chatClient,
                configuredProperties(),
                recorder,
                new ObjectMapper()
        );

        StructuredOutputGateway.StructuredOutputResult result = gateway.generate(
                new StructuredOutputGateway.StructuredOutputRequest(
                        java.util.List.of(),
                        "请输出 ParsedQuestion JSON",
                        PARSED_QUESTION_SCHEMA,
                        1
                )
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.attempts()).isEqualTo(2);
        verify(chatClient, times(2)).prompt(any(Prompt.class));
    }

    @Test
    void shouldNormalizeUnavailableFailureAndRecordAudit() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ModelCallRecordService recorder = mock(ModelCallRecordService.class);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenThrow(new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "底层调用失败"));

        SpringAiStructuredOutputGateway gateway = new SpringAiStructuredOutputGateway(
                chatClient,
                configuredProperties(),
                recorder,
                new ObjectMapper()
        );

        assertThatThrownBy(() -> gateway.generate(
                new StructuredOutputGateway.StructuredOutputRequest(
                        java.util.List.of(),
                        "请输出 ParsedQuestion JSON",
                        PARSED_QUESTION_SCHEMA,
                        0
                )
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("大模型服务暂不可用");

        verify(recorder).recordFailure(
                eq("spring-ai-openai"),
                eq("spring-ai-chat-model"),
                eq("STRUCTURED_CHAT"),
                anyString(),
                anyLong(),
                argThat(ex -> ex instanceof BusinessException businessException
                        && businessException.errorCode() == ErrorCode.MODEL_UNAVAILABLE
                        && businessException.getMessage().contains("大模型服务暂不可用"))
        );
    }

    private ModelProperties configuredProperties() {
        ModelProperties properties = new ModelProperties();
        properties.setBaseUrl("http://localhost:8089");
        properties.setApiKey("test-key");
        properties.setChatModel("spring-ai-chat-model");
        properties.setTemperature(0.2D);
        return properties;
    }
}
