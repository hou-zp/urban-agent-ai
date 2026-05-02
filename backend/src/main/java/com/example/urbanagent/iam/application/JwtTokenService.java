package com.example.urbanagent.iam.application;

import com.example.urbanagent.iam.domain.IamUser;
import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.iam.repository.IamUserRepository;
import com.example.urbanagent.iam.security.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private final JwtProperties jwtProperties;
    private final IamUserRepository iamUserRepository;
    private final UserContextResolver userContextResolver;
    private final PasswordEncoder passwordEncoder;
    private final SecretKey secretKey;

    public JwtTokenService(JwtProperties jwtProperties,
                           IamUserRepository iamUserRepository,
                           UserContextResolver userContextResolver,
                           PasswordEncoder passwordEncoder) {
        this.jwtProperties = jwtProperties;
        this.iamUserRepository = iamUserRepository;
        this.userContextResolver = userContextResolver;
        this.passwordEncoder = passwordEncoder;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    @Transactional(readOnly = true)
    public Optional<String> authenticate(String userId, String password) {
        Optional<IamUser> user = iamUserRepository.findByIdAndEnabledTrue(userId);
        if (user.isEmpty() || user.get().getPasswordHash() == null) {
            return Optional.empty();
        }
        if (!passwordEncoder.matches(password, user.get().getPasswordHash())) {
            return Optional.empty();
        }
        String token = generateToken(user.get());
        return Optional.of(token);
    }

    public String generateToken(IamUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.getExpirationHours(), ChronoUnit.HOURS);
        return Jwts.builder()
                .subject(user.getId())
                .claim("role", user.getRoleCode())
                .claim("region", user.getRegionCode())
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public Optional<UserContext> validateToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String trimmed = token.startsWith("Bearer ") ? token.substring(7) : token;
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(trimmed)
                    .getPayload();
            String userId = claims.getSubject();
            String role = claims.get("role", String.class);
            String region = claims.get("region", String.class);
            UserContext userContext = userContextResolver.resolve(userId, role, region);
            return Optional.of(userContext);
        } catch (ExpiredJwtException ex) {
            log.debug("JWT expired: {}", ex.getMessage());
            return Optional.empty();
        } catch (JwtException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public void encodeAndSetPassword(IamUser user, String rawPassword) {
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
    }
}