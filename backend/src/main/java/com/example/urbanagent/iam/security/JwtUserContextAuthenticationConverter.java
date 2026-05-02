package com.example.urbanagent.iam.security;

import com.example.urbanagent.iam.application.UserContextResolver;
import com.example.urbanagent.iam.domain.UserContext;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Objects;

public class JwtUserContextAuthenticationConverter implements Converter<Jwt, UsernamePasswordAuthenticationToken> {

    private final UserContextResolver userContextResolver;
    private final OAuth2SecurityProperties properties;
    private final ExternalRoleMappingService externalRoleMappingService;

    public JwtUserContextAuthenticationConverter(UserContextResolver userContextResolver,
                                                 OAuth2SecurityProperties properties,
                                                 ExternalRoleMappingService externalRoleMappingService) {
        this.userContextResolver = userContextResolver;
        this.properties = properties;
        this.externalRoleMappingService = externalRoleMappingService;
    }

    @Override
    public UsernamePasswordAuthenticationToken convert(Jwt jwt) {
        OAuth2SecurityProperties.JwtClaims claims = properties.getClaims();
        ExternalRoleMappingService.ResolvedRoleMapping resolvedRoleMapping = externalRoleMappingService.resolve(
                claimValue(jwt, claims.getRole()),
                claimValue(jwt, claims.getRegion())
        );
        UserContext userContext = userContextResolver.resolve(
                claimValue(jwt, claims.getUserId()),
                resolvedRoleMapping.role(),
                resolvedRoleMapping.region()
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userContext,
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + userContext.role()))
        );
        authentication.setDetails(userContext);
        return authentication;
    }

    private String claimValue(Jwt jwt, String claimName) {
        if (claimName == null || claimName.isBlank()) {
            return null;
        }
        Object value = jwt.getClaims().get(claimName);
        return value == null ? null : Objects.toString(value, null);
    }
}
