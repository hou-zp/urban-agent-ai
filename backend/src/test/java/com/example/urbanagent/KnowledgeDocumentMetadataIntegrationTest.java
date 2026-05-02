package com.example.urbanagent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:urban-agent-knowledge-metadata-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class KnowledgeDocumentMetadataIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldPersistSecurityLevelAttachmentAndSourceUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                MediaType.TEXT_PLAIN_VALUE,
                """
                柯桥区餐饮油烟治理工作规范
                对重点商户应当建立巡查和复核台账。
                """.getBytes()
        );

        mockMvc.perform(multipart("/api/v1/knowledge/documents")
                        .file(file)
                        .param("title", "柯桥区餐饮油烟治理工作规范")
                        .param("category", "POLICY")
                        .param("securityLevel", "CONFIDENTIAL")
                        .param("regionCode", "shaoxing-keqiao")
                        .param("attachmentRef", "knowledge://attachments/kq-oil-fume-policy")
                        .param("sourceUrl", "https://example.gov.cn/policy/kq-oil-fume")
                        .param("sourceOrg", "柯桥区综合执法局")
                        .param("documentNumber", "柯综执〔2026〕5号"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.securityLevel").value("confidential"))
                .andExpect(jsonPath("$.data.attachmentRef").value("knowledge://attachments/kq-oil-fume-policy"))
                .andExpect(jsonPath("$.data.sourceUrl").value("https://example.gov.cn/policy/kq-oil-fume"));

        mockMvc.perform(get("/api/v1/knowledge/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].securityLevel").exists())
                .andExpect(jsonPath("$.data[0].attachmentRef").exists())
                .andExpect(jsonPath("$.data[0].sourceUrl").exists());
    }
}
