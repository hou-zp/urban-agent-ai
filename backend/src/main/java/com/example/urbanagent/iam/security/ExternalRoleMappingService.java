package com.example.urbanagent.iam.security;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
public class ExternalRoleMappingService {

    private final OAuth2SecurityProperties properties;

    public ExternalRoleMappingService(OAuth2SecurityProperties properties) {
        this.properties = properties;
    }

    public ResolvedRoleMapping resolve(String externalRole, String externalRegion) {
        OAuth2SecurityProperties.RoleMapping mapping = findMapping(externalRole);
        if (mapping == null) {
            return new ResolvedRoleMapping(externalRole, externalRegion, false);
        }
        String resolvedRole = blankToNull(mapping.getRole());
        String resolvedRegion = blankToNull(mapping.getRegion());
        return new ResolvedRoleMapping(
                resolvedRole == null ? externalRole : resolvedRole,
                resolvedRegion == null ? externalRegion : resolvedRegion,
                true
        );
    }

    private OAuth2SecurityProperties.RoleMapping findMapping(String externalRole) {
        if (externalRole == null || externalRole.isBlank()) {
            return null;
        }
        Map<String, OAuth2SecurityProperties.RoleMapping> mappings = properties.getRoleMappings();
        return mappings.get(externalRole.trim().toLowerCase(Locale.ROOT));
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record ResolvedRoleMapping(String role, String region, boolean mapped) {
    }
}
