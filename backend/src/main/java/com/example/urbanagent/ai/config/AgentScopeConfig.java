package com.example.urbanagent.ai.config;

import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentScopeProperties.class)
public class AgentScopeConfig {

    @Bean
    public AgentScopeModelFactory agentScopeModelFactory(AgentScopeProperties properties) {
        return new AgentScopeModelFactory(properties);
    }

    public static class AgentScopeModelFactory {

        private final AgentScopeProperties properties;

        public AgentScopeModelFactory(AgentScopeProperties properties) {
            this.properties = properties;
        }

        public boolean enabled() {
            return properties.isEnabled()
                    && properties.getApiKey() != null
                    && !properties.getApiKey().isBlank()
                    && properties.getModelName() != null
                    && !properties.getModelName().isBlank();
        }

        public OpenAIChatModel createChatModel() {
            OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                    .apiKey(properties.getApiKey())
                    .modelName(properties.getModelName());
            if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
                builder.baseUrl(properties.getBaseUrl());
            }
            return builder.build();
        }
    }
}
