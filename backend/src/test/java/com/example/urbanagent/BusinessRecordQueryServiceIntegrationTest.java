package com.example.urbanagent;

import com.example.urbanagent.agent.tool.BusinessRecordAgentTools;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.example.urbanagent.query.application.DataCatalogApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:urban-agent-record-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "urban-agent.model.provider=mock"
})
class BusinessRecordQueryServiceIntegrationTest {

    @Autowired
    private DataCatalogApplicationService dataCatalogApplicationService;

    @Autowired
    private BusinessRecordAgentTools businessRecordAgentTools;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dataCatalogApplicationService.syncDemoCatalog();
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void shouldHideUnauthorizedMerchantFieldsForOfficer() throws Exception {
        UserContextHolder.set(new UserContext("officer-user", "OFFICER", "shaoxing-keqiao"));

        String payload = businessRecordAgentTools.businessRecordQuery(
                "MERCHANT",
                "齐贤",
                null,
                null,
                "abnormal",
                null,
                10
        );
        JsonNode root = objectMapper.readTree(payload);

        assertThat(root.path("recordType").asText()).isEqualTo("MERCHANT");
        assertThat(root.path("fields").toString()).doesNotContain("contact_name");
        assertThat(root.path("fields").toString()).doesNotContain("contact_phone");
        assertThat(root.path("rows").get(0).toString()).doesNotContain("contact_name");
        assertThat(root.path("rows").get(0).toString()).doesNotContain("contact_phone");
        assertThat(root.path("rows").get(0).path("UNIT_NAME").asText()).isEqualTo("齐贤快餐");
    }

    @Test
    void shouldMaskMerchantSensitiveFieldsForManager() throws Exception {
        UserContextHolder.set(new UserContext("manager-user", "MANAGER", "shaoxing-keqiao"));

        String payload = businessRecordAgentTools.businessRecordQuery(
                "MERCHANT",
                "柯香",
                null,
                null,
                "online",
                null,
                10
        );
        JsonNode root = objectMapper.readTree(payload);

        assertThat(root.path("maskedFields").toString()).contains("contact_name", "contact_phone");
        assertThat(root.path("rows").get(0).path("CONTACT_NAME").asText()).isEqualTo("张*");
        assertThat(root.path("rows").get(0).path("CONTACT_PHONE").asText()).isEqualTo("139****1234");
    }

    @Test
    void shouldMaskInternalPointNoteForManager() throws Exception {
        UserContextHolder.set(new UserContext("manager-user", "MANAGER", "shaoxing-keqiao"));

        String payload = businessRecordAgentTools.businessRecordQuery(
                "POINT",
                null,
                null,
                null,
                "abnormal",
                null,
                10
        );
        JsonNode root = objectMapper.readTree(payload);

        assertThat(root.path("rows").size()).isGreaterThan(0);
        assertThat(root.path("maskedFields").toString()).contains("issue_note");
        assertThat(root.path("rows").get(0).path("ISSUE_NOTE").asText()).contains("已脱敏");
    }

    @Test
    void shouldRejectUnsupportedLandPlotDetailQuery() {
        UserContextHolder.set(new UserContext("manager-user", "MANAGER", "shaoxing-keqiao"));

        assertThatThrownBy(() -> businessRecordAgentTools.businessRecordQuery(
                "LAND_PLOT",
                null,
                null,
                null,
                null,
                null,
                10
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void shouldExposeBusinessRecordQueryApi() throws Exception {
        String response = mockMvc.perform(post("/api/v1/data/query/records")
                        .header("X-User-Role", "MANAGER")
                        .header("X-User-Region", "shaoxing-keqiao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recordType": "MERCHANT",
                                  "keyword": "柯香",
                                  "status": "online",
                                  "limit": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response).path("data");
        assertThat(root.path("recordType").asText()).isEqualTo("MERCHANT");
        assertThat(root.path("recordQueryId").asText()).startsWith("record-");
        assertThat(root.path("fields").isArray()).isTrue();
        assertThat(root.path("rows").isArray()).isTrue();
        assertThat(root.path("maskedFields").toString()).contains("contact_name", "contact_phone");
    }
}
