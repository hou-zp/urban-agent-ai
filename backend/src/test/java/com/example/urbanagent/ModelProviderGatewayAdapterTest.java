package com.example.urbanagent;

import com.example.urbanagent.ai.application.ModelProvider;
import com.example.urbanagent.ai.application.ModelProviderGatewayAdapter;
import com.example.urbanagent.ai.application.StructuredOutputGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelProviderGatewayAdapterTest {

    @Test
    void shouldRetryStructuredOutputUntilJsonBecomesValid() {
        ModelProvider modelProvider = mock(ModelProvider.class);
        when(modelProvider.structuredChat(anyList(), anyString(), anyString()))
                .thenReturn("not-json")
                .thenReturn("{\"answer\":\"ok\"}");

        ModelProviderGatewayAdapter adapter = new ModelProviderGatewayAdapter(modelProvider, new ObjectMapper());

        StructuredOutputGateway.StructuredOutputResult result = adapter.generate(
                new StructuredOutputGateway.StructuredOutputRequest(List.of("历史问题"), "请输出结构化内容", "{ \"type\": \"object\" }", 2)
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.validationError()).isNull();
        assertThat(result.content()).contains("\"answer\":\"ok\"");
        verify(modelProvider, times(2)).structuredChat(anyList(), anyString(), anyString());
    }

    @Test
    void shouldReturnValidationErrorWhenStructuredOutputStillInvalidAfterRetries() {
        ModelProvider modelProvider = mock(ModelProvider.class);
        when(modelProvider.structuredChat(anyList(), anyString(), anyString()))
                .thenReturn("bad-json");

        ModelProviderGatewayAdapter adapter = new ModelProviderGatewayAdapter(modelProvider, new ObjectMapper());

        StructuredOutputGateway.StructuredOutputResult result = adapter.generate(
                new StructuredOutputGateway.StructuredOutputRequest(List.of(), "请输出结构化内容", "{ \"type\": \"object\" }", 2)
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.attempts()).isEqualTo(3);
        assertThat(result.validationError()).isEqualTo("模型未返回合法 JSON");
        assertThat(result.content()).isEqualTo("bad-json");
        verify(modelProvider, times(3)).structuredChat(anyList(), anyString(), anyString());
    }
}
