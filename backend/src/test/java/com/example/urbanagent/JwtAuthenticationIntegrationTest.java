package com.example.urbanagent;

import com.example.urbanagent.agent.domain.AgentSession;
import com.example.urbanagent.agent.repository.AgentSessionRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:urban-agent-jwt-auth-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "urban-agent.security.oauth2.enabled=true",
        "urban-agent.security.oauth2.role-mappings.grid_inspector.role=OFFICER",
        "urban-agent.security.oauth2.role-mappings.city_manager.role=MANAGER",
        "urban-agent.security.oauth2.role-mappings.city_manager.region=city",
        "spring.security.oauth2.resourceserver.jwt.public-key-location=classpath:security/test-jwt-public.pem"
})
class JwtAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentSessionRepository agentSessionRepository;

    @Test
    void shouldCreateSessionUsingJwtIdentity() throws Exception {
        String token = signedToken("jwt-manager", "MANAGER", "shaoxing-keqiao");

        String response = mockMvc.perform(post("/api/v1/agent/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"JWT 会话测试"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = response.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");
        AgentSession session = agentSessionRepository.findById(sessionId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(session.getUserId()).isEqualTo("jwt-manager");
    }

    @Test
    void shouldUseJwtRoleAndRegionForPermissionChecks() throws Exception {
        String token = signedToken("jwt-officer", "OFFICER", "district-a");

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询投诉人电话","sql":"select reporter_phone from fact_complaint_order where report_date between '2026-04-01' and '2026-04-07' limit 10"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40004))
                .andExpect(jsonPath("$.message").value(containsString("字段无访问权限")));
    }

    @Test
    void shouldMapExternalJwtRoleToInternalOfficerRole() throws Exception {
        String token = signedToken("jwt-grid", "grid_inspector", "district-a");

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"外部角色映射后查询投诉人电话","sql":"select reporter_phone from fact_complaint_order where report_date between '2026-04-01' and '2026-04-07' limit 10"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40004))
                .andExpect(jsonPath("$.message").value(containsString("字段无访问权限")));
    }

    @Test
    void shouldOverrideRegionWhenExternalRoleMappingDefinesFixedRegion() throws Exception {
        String token = signedToken("jwt-city-manager", "city_manager", "district-b");

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"映射后按全市权限查询投诉排行","sql":"select street_name, sum(complaint_count) as metric_value from fact_complaint_order where report_date between '2026-04-01' and '2026-04-07' group by street_name limit 10"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executedSql").value(containsString("region_code = 'city'")))
                .andExpect(jsonPath("$.data.dataStatement.permissionRewrite").value(containsString("region_code = 'city'")));
    }

    @Test
    void shouldRejectJwtWithInvalidSignature() throws Exception {
        String invalidToken = corruptSignature(signedToken("jwt-manager", "MANAGER", "shaoxing-keqiao"));

        mockMvc.perform(post("/api/v1/agent/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(invalidToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"JWT 非法签名"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002))
                .andExpect(jsonPath("$.message").value("未通过身份校验"));
    }

    private String signedToken(String userId, String role, String region) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId)
                .claim("role", role)
                .claim("region", region)
                .issuer("https://issuer.example.test")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600)))
                .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        signedJwt.sign(new RSASSASigner((RSAPrivateKey) loadPrivateKey()));
        return signedJwt.serialize();
    }

    private String corruptSignature(String token) {
        String[] parts = token.split("\\.");
        String signature = parts[2];
        char replacement = signature.charAt(0) == 'A' ? 'B' : 'A';
        parts[2] = replacement + signature.substring(1);
        return String.join(".", parts);
    }

    private String bearerToken(String token) {
        return "Bearer " + token;
    }

    private PrivateKey loadPrivateKey() throws Exception {
        String pem;
        try (InputStream inputStream = new ClassPathResource("security/test-jwt-private.pem").getInputStream()) {
            pem = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }
}
