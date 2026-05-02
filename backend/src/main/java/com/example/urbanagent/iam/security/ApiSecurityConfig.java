package com.example.urbanagent.iam.security;

import com.example.urbanagent.common.api.ApiResponse;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.iam.application.UserContextResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableConfigurationProperties({OAuth2SecurityProperties.class, HeaderAuthSecurityProperties.class})
@EnableMethodSecurity
public class ApiSecurityConfig {

    private final ObjectMapper objectMapper;
    private final OAuth2SecurityProperties oauth2SecurityProperties;

    public ApiSecurityConfig(ObjectMapper objectMapper,
                             OAuth2SecurityProperties oauth2SecurityProperties) {
        this.objectMapper = objectMapper;
        this.oauth2SecurityProperties = oauth2SecurityProperties;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            HeaderUserAuthenticationFilter headerUserAuthenticationFilter,
                                            UserContextFilter userContextFilter,
                                            JwtUserContextAuthenticationConverter jwtUserContextAuthenticationConverter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher("/actuator/health"),
                                AntPathRequestMatcher.antMatcher("/actuator/health/**"),
                                AntPathRequestMatcher.antMatcher("/error")
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.ACCESS_DENIED, "未通过身份校验"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.ACCESS_DENIED, "当前身份无访问权限"))
                );
        if (oauth2SecurityProperties.isEnabled()) {
            http.oauth2ResourceServer(oauth2 -> oauth2
                    .authenticationEntryPoint((request, response, ex) ->
                            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.ACCESS_DENIED, "未通过身份校验"))
                    .accessDeniedHandler((request, response, ex) ->
                            writeError(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.ACCESS_DENIED, "当前身份无访问权限"))
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtUserContextAuthenticationConverter)));
        }
        http
                .addFilterBefore(headerUserAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(userContextFilter, HeaderUserAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    HeaderUserAuthenticationFilter headerUserAuthenticationFilter(UserContextResolver userContextResolver,
                                                                  ObjectMapper objectMapper,
                                                                  HeaderAuthSecurityProperties headerAuthSecurityProperties,
                                                                  OAuth2SecurityProperties oauth2SecurityProperties) {
        return new HeaderUserAuthenticationFilter(userContextResolver, objectMapper, headerAuthSecurityProperties, oauth2SecurityProperties);
    }

    @Bean
    UserContextFilter userContextFilter(UserContextResolver userContextResolver,
                                        ObjectMapper objectMapper,
                                        HeaderAuthSecurityProperties headerAuthSecurityProperties,
                                        OAuth2SecurityProperties oauth2SecurityProperties) {
        return new UserContextFilter(userContextResolver, objectMapper, headerAuthSecurityProperties, oauth2SecurityProperties);
    }

    @Bean
    JwtUserContextAuthenticationConverter jwtUserContextAuthenticationConverter(UserContextResolver userContextResolver,
                                                                                OAuth2SecurityProperties properties,
                                                                                ExternalRoleMappingService externalRoleMappingService) {
        return new JwtUserContextAuthenticationConverter(userContextResolver, properties, externalRoleMappingService);
    }

    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    private void writeError(HttpServletResponse response,
                            int status,
                            ErrorCode errorCode,
                            String message) throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.failure(errorCode.code(), message)
        );
    }
}
