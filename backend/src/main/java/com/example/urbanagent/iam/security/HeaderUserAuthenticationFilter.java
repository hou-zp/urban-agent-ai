package com.example.urbanagent.iam.security;

import com.example.urbanagent.common.api.ApiResponse;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.iam.application.UserContextResolver;
import com.example.urbanagent.iam.domain.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class HeaderUserAuthenticationFilter extends OncePerRequestFilter {

    private final UserContextResolver userContextResolver;
    private final ObjectMapper objectMapper;
    private final HeaderAuthSecurityProperties headerAuthSecurityProperties;
    private final OAuth2SecurityProperties oauth2SecurityProperties;

    public HeaderUserAuthenticationFilter(UserContextResolver userContextResolver,
                                          ObjectMapper objectMapper,
                                          HeaderAuthSecurityProperties headerAuthSecurityProperties,
                                          OAuth2SecurityProperties oauth2SecurityProperties) {
        this.userContextResolver = userContextResolver;
        this.objectMapper = objectMapper;
        this.headerAuthSecurityProperties = headerAuthSecurityProperties;
        this.oauth2SecurityProperties = oauth2SecurityProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!headerAuthSecurityProperties.isEnabled() || shouldDeferToBearerToken(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserContext userContext = userContextResolver.resolve(
                        request.getHeader("X-User-Id"),
                        request.getHeader("X-User-Role"),
                        request.getHeader("X-User-Region")
                );
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userContext,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + userContext.role()))
                );
                authentication.setDetails(userContext);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException ex) {
                writeError(response, ex);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldDeferToBearerToken(HttpServletRequest request) {
        return oauth2SecurityProperties.isEnabled() && hasBearerToken(request);
    }

    private boolean hasBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return authorization != null && authorization.startsWith("Bearer ");
    }

    private void writeError(HttpServletResponse response, BusinessException ex) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.failure(ex.errorCode().code(), ex.getMessage())
        );
    }
}
