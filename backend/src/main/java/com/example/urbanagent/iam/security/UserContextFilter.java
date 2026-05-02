package com.example.urbanagent.iam.security;

import com.example.urbanagent.common.api.ApiResponse;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.logging.LoggingContext;
import com.example.urbanagent.iam.application.UserContextResolver;
import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class UserContextFilter extends OncePerRequestFilter {

    private final UserContextResolver userContextResolver;
    private final ObjectMapper objectMapper;
    private final HeaderAuthSecurityProperties headerAuthSecurityProperties;
    private final OAuth2SecurityProperties oauth2SecurityProperties;

    public UserContextFilter(UserContextResolver userContextResolver,
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
        UserContext userContext;
        try {
            userContext = resolveUserContext(request);
        } catch (BusinessException ex) {
            writeError(response, ex);
            return;
        }

        if (userContext == null) {
            filterChain.doFilter(request, response);
            return;
        }

        UserContextHolder.set(userContext);
        LoggingContext.put("userId", userContext.userId());
        LoggingContext.put("role", userContext.role());
        LoggingContext.put("region", userContext.region());
        try {
            filterChain.doFilter(request, response);
        } finally {
            LoggingContext.remove("userId");
            LoggingContext.remove("role");
            LoggingContext.remove("region");
            UserContextHolder.clear();
        }
    }

    private UserContext resolveUserContext(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserContext userContext) {
                return userContext;
            }
            Object details = authentication.getDetails();
            if (details instanceof UserContext userContext) {
                return userContext;
            }
        }
        if (!headerAuthSecurityProperties.isEnabled() || shouldDeferToBearerToken(request)) {
            return null;
        }
        return userContextResolver.resolve(
                request.getHeader("X-User-Id"),
                request.getHeader("X-User-Role"),
                request.getHeader("X-User-Region")
        );
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
