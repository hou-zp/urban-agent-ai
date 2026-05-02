package com.example.urbanagent.iam.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "urban-agent.security.jwt")
public class JwtProperties {

    private String secret = "please-override-urban-agent-jwt-secret-in-production-32chars";
    private int expirationHours = 24;
    private String issuer = "urban-agent";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getExpirationHours() {
        return expirationHours;
    }

    public void setExpirationHours(int expirationHours) {
        this.expirationHours = expirationHours;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}