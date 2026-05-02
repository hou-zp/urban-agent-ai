package com.example.urbanagent.query.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "urban-agent.query.rest-adapters")
public class RestBusinessDataAdapterProperties {

    private Map<String, RestAdapterConfig> configs = new LinkedHashMap<>();

    public Map<String, RestAdapterConfig> getConfigs() {
        return configs;
    }

    public void setConfigs(Map<String, RestAdapterConfig> configs) {
        this.configs = configs;
    }

    public RestAdapterConfig find(String connectionRef) {
        if (connectionRef == null || connectionRef.isBlank()) {
            return null;
        }
        return configs.get(connectionRef);
    }

    public static class RestAdapterConfig {

        private String endpoint;
        private String authRef;
        private Integer timeoutSeconds;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAuthRef() {
            return authRef;
        }

        public void setAuthRef(String authRef) {
            this.authRef = authRef;
        }

        public Integer getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(Integer timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
