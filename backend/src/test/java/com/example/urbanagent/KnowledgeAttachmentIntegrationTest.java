package com.example.urbanagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:urban-agent-knowledge-attachment-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "urban-agent.object-storage.local-base-path=./target/object-storage-attachment-test"
})
class KnowledgeAttachmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldUploadAndDownloadAttachmentWithAuditTrail() throws Exception {
        String documentId = createDocument("PUBLIC", "district-a", "附件闭环测试");
        MockMultipartFile attachment = new MockMultipartFile(
                "file",
                "policy-original.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "pdf-binary-demo".getBytes()
        );

        String uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge/documents/{documentId}/attachment", documentId)
                        .file(attachment)
                        .header("X-User-Id", "manager-a")
                        .header("X-User-Role", "MANAGER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attachmentRef").value(org.hamcrest.Matchers.startsWith("object://knowledge-attachments/")))
                .andExpect(jsonPath("$.data.fileName").value("policy-original.pdf"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode uploadJson = objectMapper.readTree(uploadResponse);
        assertThat(uploadJson.path("data").path("sizeBytes").asLong()).isEqualTo("pdf-binary-demo".getBytes().length);

        mockMvc.perform(get("/api/v1/knowledge/documents/{documentId}/attachment", documentId)
                        .header("X-User-Id", "officer-a")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("policy-original.pdf")))
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo("pdf-binary-demo".getBytes()));

        assertThat(countAudit("knowledge.attachment.upload", documentId)).isEqualTo(1);
        assertThat(countAudit("knowledge.attachment.download", documentId)).isEqualTo(1);
    }

    @Test
    void shouldRejectInternalAttachmentDownloadForOfficer() throws Exception {
        String documentId = createDocument("INTERNAL", "district-a", "内部附件权限测试");
        MockMultipartFile attachment = new MockMultipartFile(
                "file",
                "internal-note.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx-binary-demo".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/knowledge/documents/{documentId}/attachment", documentId)
                        .file(attachment)
                        .header("X-User-Id", "manager-a")
                        .header("X-User-Role", "MANAGER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/knowledge/documents/{documentId}/attachment", documentId)
                        .header("X-User-Id", "officer-a")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10002));
    }

    private String createDocument(String securityLevel, String regionCode, String title) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "knowledge.txt",
                MediaType.TEXT_PLAIN_VALUE,
                """
                城市管理业务知识
                对重点区域应当开展日常巡查并留痕。
                """.getBytes()
        );
        String response = mockMvc.perform(multipart("/api/v1/knowledge/documents")
                        .file(file)
                        .param("title", title)
                        .param("category", "POLICY")
                        .param("sourceOrg", "市城管局")
                        .param("documentNumber", "城管规〔2026〕518号-" + title)
                        .param("securityLevel", securityLevel)
                        .param("regionCode", regionCode))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asText();
    }

    private int countAudit(String actionType, String evidenceId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where action_type = ? and evidence_id = ?",
                Integer.class,
                actionType,
                evidenceId
        );
        return count == null ? 0 : count;
    }
}
