package com.example.urbanagent;

import com.example.urbanagent.ai.application.ChatModelGateway;
import com.example.urbanagent.ai.application.EmbeddingGateway;
import com.example.urbanagent.ai.application.ModelChunk;
import com.example.urbanagent.ai.application.ModelProvider;
import com.example.urbanagent.ai.application.ModelProviderGatewayAdapter;
import com.example.urbanagent.ai.application.StructuredOutputGateway;
import com.example.urbanagent.ai.config.AiGatewayProperties;
import com.example.urbanagent.ai.config.AiGatewaySelectionConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AiGatewaySelectionConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LegacyGatewayTestConfig.class, AiGatewaySelectionConfig.class);

    @Test
    void shouldUseLegacyGatewaysByDefault() {
        contextRunner.run(context -> {
            ChatModelGateway chatModelGateway = context.getBean(ChatModelGateway.class);
            EmbeddingGateway embeddingGateway = context.getBean(EmbeddingGateway.class);
            StructuredOutputGateway structuredOutputGateway = context.getBean(StructuredOutputGateway.class);

            assertThat(chatModelGateway.name()).isEqualTo("legacy-test-model");
            assertThat(embeddingGateway.embeddingModelName()).isEqualTo("legacy-test-model");
            assertThat(structuredOutputGateway.generate(List.of(), "prompt", "{ \"type\": \"object\" }").content())
                    .contains("legacy");
        });
    }

    @Test
    void shouldSwitchToSpringAiGatewaysWhenConfigured() {
        contextRunner
                .withPropertyValues("urban-agent.ai.gateway.type=spring_ai")
                .withUserConfiguration(SpringAiGatewayStubConfig.class)
                .run(context -> {
                    assertThat(context.getBean(ChatModelGateway.class).name()).isEqualTo("spring-ai-chat");
                    assertThat(context.getBean(EmbeddingGateway.class).embeddingModelName())
                            .isEqualTo("spring-ai-embedding");
                    assertThat(context.getBean(StructuredOutputGateway.class)
                            .generate(List.of(), "prompt", "{ \"type\": \"object\" }").content())
                            .contains("spring-ai");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class LegacyGatewayTestConfig {

        @Bean
        ModelProvider modelProvider() {
            return new ModelProvider() {
                @Override
                public String name() {
                    return "legacy-test-model";
                }

                @Override
                public String chat(List<String> history, String prompt) {
                    return "legacy-chat";
                }

                @Override
                public Stream<ModelChunk> streamChat(List<String> history, String prompt) {
                    return Stream.of(new ModelChunk("legacy-chat", true));
                }

                @Override
                public String structuredChat(List<String> history, String prompt, String jsonSchema) {
                    return "{\"answer\":\"legacy\"}";
                }

                @Override
                public float[] embed(String content) {
                    return new float[]{1F, 2F};
                }
            };
        }

        @Bean(name = AiGatewaySelectionConfig.LEGACY_GATEWAY_ADAPTER)
        ModelProviderGatewayAdapter legacyModelGatewayAdapter(ModelProvider modelProvider) {
            return new ModelProviderGatewayAdapter(modelProvider, new ObjectMapper());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SpringAiGatewayStubConfig {

        @Bean(name = AiGatewaySelectionConfig.SPRING_AI_CHAT_MODEL_GATEWAY)
        ChatModelGateway springAiChatModelGateway() {
            return new ChatModelGateway() {
                @Override
                public String name() {
                    return "spring-ai-chat";
                }

                @Override
                public String chat(List<String> history, String prompt) {
                    return "spring-ai-chat";
                }

                @Override
                public Stream<ModelChunk> streamChat(List<String> history, String prompt) {
                    return Stream.of(new ModelChunk("spring-ai-chat", true));
                }
            };
        }

        @Bean(name = AiGatewaySelectionConfig.SPRING_AI_EMBEDDING_GATEWAY)
        EmbeddingGateway springAiEmbeddingGateway() {
            return new EmbeddingGateway() {
                @Override
                public String embeddingModelName() {
                    return "spring-ai-embedding";
                }

                @Override
                public float[] embed(String content) {
                    return new float[]{9F, 9F};
                }
            };
        }

        @Bean(name = AiGatewaySelectionConfig.SPRING_AI_STRUCTURED_OUTPUT_GATEWAY)
        StructuredOutputGateway springAiStructuredOutputGateway() {
            return request -> new StructuredOutputGateway.StructuredOutputResult("{\"answer\":\"spring-ai\"}", 1, true, null);
        }
    }
}
