package com.example.urbanagent.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "urban-agent.ai.gateway")
public class AiGatewayProperties {

    private GatewayType type = GatewayType.LEGACY;

    public GatewayType getType() {
        return type;
    }

    public void setType(GatewayType type) {
        this.type = type == null ? GatewayType.LEGACY : type;
    }

    public enum GatewayType {
        LEGACY,
        SPRING_AI
    }
}
