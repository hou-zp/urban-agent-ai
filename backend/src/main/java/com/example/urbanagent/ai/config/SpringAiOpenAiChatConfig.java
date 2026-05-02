package com.example.urbanagent.ai.config;

import com.example.urbanagent.ai.application.ChatModelGateway;
import com.example.urbanagent.ai.application.ModelCallRecordService;
import com.example.urbanagent.ai.application.ModelProperties;
import com.example.urbanagent.ai.application.SpringAiChatModelGateway;
import com.example.urbanagent.ai.application.SpringAiStructuredOutputGateway;
import com.example.urbanagent.ai.application.StructuredOutputGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

@Configuration
@ConditionalOnProperty(prefix = "urban-agent.ai.gateway", name = "type", havingValue = "spring_ai")
public class SpringAiOpenAiChatConfig {

    @Bean("springAiOpenAiApi")
    public OpenAiApi springAiOpenAiApi(ModelProperties properties,
                                       RestClient.Builder restClientBuilder,
                                       ObjectProvider<WebClient.Builder> webClientBuilderProvider) {
        return OpenAiApi.builder()
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .apiKey(blankToEmpty(properties.getApiKey()))
                .completionsPath(normalizePath(properties.getChatPath()))
                .embeddingsPath(normalizePath(properties.getEmbeddingsPath()))
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
                .build();
    }

    @Bean("springAiOpenAiChatModel")
    public OpenAiChatModel springAiOpenAiChatModel(@Qualifier("springAiOpenAiApi") OpenAiApi openAiApi,
                                                   ModelProperties properties,
                                                   ObservationRegistry observationRegistry) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.getChatModel())
                .temperature(properties.getTemperature())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .observationRegistry(observationRegistry)
                .build();
    }

    @Bean("springAiChatClient")
    public ChatClient springAiChatClient(@Qualifier("springAiOpenAiChatModel") OpenAiChatModel chatModel,
                                         ObservationRegistry observationRegistry) {
        return ChatClient.create(chatModel, observationRegistry);
    }

    @Bean(AiGatewaySelectionConfig.SPRING_AI_CHAT_MODEL_GATEWAY)
    public ChatModelGateway springAiChatModelGateway(@Qualifier("springAiChatClient") ChatClient chatClient,
                                                     ModelProperties properties,
                                                     ModelCallRecordService modelCallRecordService) {
        return new SpringAiChatModelGateway(chatClient, properties, modelCallRecordService);
    }

    @Bean(AiGatewaySelectionConfig.SPRING_AI_STRUCTURED_OUTPUT_GATEWAY)
    public StructuredOutputGateway springAiStructuredOutputGateway(@Qualifier("springAiChatClient") ChatClient chatClient,
                                                                   ModelProperties properties,
                                                                   ModelCallRecordService modelCallRecordService,
                                                                   ObjectMapper objectMapper) {
        return new SpringAiStructuredOutputGateway(chatClient, properties, modelCallRecordService, objectMapper);
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
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

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
