package com.example.urbanagent;

import com.example.urbanagent.ai.application.ModelCallRecordService;
import com.example.urbanagent.ai.application.ModelProperties;
import com.example.urbanagent.ai.application.SpringAiChatModelGateway;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiChatModelGatewayTest {

    @Test
    void shouldCallChatClientAndRecordSuccess() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ModelCallRecordService recorder = mock(ModelCallRecordService.class);
        ModelProperties properties = configuredProperties();
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("已生成可信答复");

        SpringAiChatModelGateway gateway = new SpringAiChatModelGateway(chatClient, properties, recorder);

        String response = gateway.chat(List.of("历史问题"), "请汇总当前处置建议");

        assertThat(response).isEqualTo("已生成可信答复");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());
        Prompt prompt = promptCaptor.getValue();
        assertThat(prompt.getInstructions()).hasSize(3);
        assertThat(prompt.getSystemMessage().getText()).contains("城管业务智能助手");
        assertThat(prompt.getUserMessages().get(1).getText()).isEqualTo("请汇总当前处置建议");
        verify(recorder).recordSuccess(
                eq("spring-ai-openai"),
                eq("spring-ai-chat-model"),
                eq("CHAT"),
                eq("历史问题\n请汇总当前处置建议"),
                eq("已生成可信答复"),
                anyLong()
        );
    }

    @Test
    void shouldNormalizeUnavailableFailureAndRecordAudit() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ModelCallRecordService recorder = mock(ModelCallRecordService.class);
        ModelProperties properties = configuredProperties();
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenThrow(new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "底层调用失败"));

        SpringAiChatModelGateway gateway = new SpringAiChatModelGateway(chatClient, properties, recorder);

        assertThatThrownBy(() -> gateway.chat(List.of(), "请生成回复"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("大模型服务暂不可用");

        verify(recorder).recordFailure(
                eq("spring-ai-openai"),
                eq("spring-ai-chat-model"),
                eq("CHAT"),
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
