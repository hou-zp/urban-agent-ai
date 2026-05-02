package com.example.urbanagent.iam.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "urban-agent.security.oauth2")
public class OAuth2SecurityProperties {

    private boolean enabled;
    private final JwtClaims claims = new JwtClaims();
    private final Map<String, RoleMapping> roleMappings = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public JwtClaims getClaims() {
        return claims;
    }

    public Map<String, RoleMapping> getRoleMappings() {
        return roleMappings;
    }

    public static class JwtClaims {

        private String userId = "sub";
        private String role = "role";
        private String region = "region";

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }
    }

    public static class RoleMapping {

        private String role;
        private String region;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }
    }
}
