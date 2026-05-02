package com.example.urbanagent.ai.config;

import com.example.urbanagent.ai.application.ChatModelGateway;
import com.example.urbanagent.ai.application.EmbeddingGateway;
import com.example.urbanagent.ai.application.StructuredOutputGateway;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(AiGatewayProperties.class)
public class AiGatewaySelectionConfig {

    public static final String LEGACY_GATEWAY_ADAPTER = "legacyModelGatewayAdapter";
    public static final String SPRING_AI_CHAT_MODEL_GATEWAY = "springAiChatModelGateway";
    public static final String SPRING_AI_EMBEDDING_GATEWAY = "springAiEmbeddingGateway";
    public static final String SPRING_AI_STRUCTURED_OUTPUT_GATEWAY = "springAiStructuredOutputGateway";

    private final AiGatewayProperties properties;
    private final BeanFactory beanFactory;

    public AiGatewaySelectionConfig(AiGatewayProperties properties, BeanFactory beanFactory) {
        this.properties = properties;
        this.beanFactory = beanFactory;
    }

    @Bean
    @Primary
    public ChatModelGateway chatModelGateway(@Qualifier(LEGACY_GATEWAY_ADAPTER) ChatModelGateway legacyGateway) {
        if (properties.getType() == AiGatewayProperties.GatewayType.SPRING_AI) {
            return new SelectedChatModelGateway(
                    beanFactory.getBean(SPRING_AI_CHAT_MODEL_GATEWAY, ChatModelGateway.class)
            );
        }
        return new SelectedChatModelGateway(legacyGateway);
    }

    @Bean
    @Primary
    public EmbeddingGateway embeddingGateway(@Qualifier(LEGACY_GATEWAY_ADAPTER) EmbeddingGateway legacyGateway) {
        if (properties.getType() == AiGatewayProperties.GatewayType.SPRING_AI) {
            return new SelectedEmbeddingGateway(
                    beanFactory.getBean(SPRING_AI_EMBEDDING_GATEWAY, EmbeddingGateway.class)
            );
        }
        return new SelectedEmbeddingGateway(legacyGateway);
    }

    @Bean
    @Primary
    public StructuredOutputGateway structuredOutputGateway(
            @Qualifier(LEGACY_GATEWAY_ADAPTER) StructuredOutputGateway legacyGateway) {
        if (properties.getType() == AiGatewayProperties.GatewayType.SPRING_AI) {
            return new SelectedStructuredOutputGateway(
                    beanFactory.getBean(SPRING_AI_STRUCTURED_OUTPUT_GATEWAY, StructuredOutputGateway.class)
            );
        }
        return new SelectedStructuredOutputGateway(legacyGateway);
    }

    static final class SelectedChatModelGateway implements ChatModelGateway {

        private final ChatModelGateway delegate;

        private SelectedChatModelGateway(ChatModelGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public String modelName() {
            return delegate.modelName();
        }

        @Override
        public String chat(java.util.List<String> history, String prompt) {
            return delegate.chat(history, prompt);
        }

        @Override
        public java.util.stream.Stream<com.example.urbanagent.ai.application.ModelChunk> streamChat(
                java.util.List<String> history, String prompt) {
            return delegate.streamChat(history, prompt);
        }
    }

    static final class SelectedEmbeddingGateway implements EmbeddingGateway {

        private final EmbeddingGateway delegate;

        private SelectedEmbeddingGateway(EmbeddingGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public String embeddingModelName() {
            return delegate.embeddingModelName();
        }

        @Override
        public float[] embed(String content) {
            return delegate.embed(content);
        }
    }

    static final class SelectedStructuredOutputGateway implements StructuredOutputGateway {

        private final StructuredOutputGateway delegate;

        private SelectedStructuredOutputGateway(StructuredOutputGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public StructuredOutputResult generate(StructuredOutputRequest request) {
            return delegate.generate(request);
        }
    }
}
