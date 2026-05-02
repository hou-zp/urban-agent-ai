package com.example.urbanagent.iam.security;

import com.example.urbanagent.iam.application.JwtTokenService;
import com.example.urbanagent.iam.domain.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 验证自定义 JWT 令牌并设置 Spring Security Context。
 * 在 HeaderUserAuthenticationFilter 之前运行。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Optional<UserContext> userContext = jwtTokenService.validateToken(token);
            if (userContext.isPresent()) {
                UserContext ctx = userContext.get();
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        ctx,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + ctx.role()))
                );
                authentication.setDetails(ctx);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 排除登录接口本身，避免循环依赖
        return request.getRequestURI().equals("/api/v1/auth/login");
    }
}