package com.example.urbanagent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:urban-agent-prod-header-auth-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class ProductionHeaderAuthenticationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectHeaderSimulatedIdentityInProductionProfile() throws Exception {
        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header("X-User-Id", "demo-user")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Region", "city")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"生产环境伪造身份","sql":"select street_name from fact_complaint_order where report_date between '2026-04-01' and '2026-04-07' limit 1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002))
                .andExpect(jsonPath("$.message").value(containsString("未通过身份校验")));
    }
}
