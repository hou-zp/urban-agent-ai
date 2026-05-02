package com.example.urbanagent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:urban-agent-citation-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class CitationRequirementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRefusePolicyProcessAnswerWhenNoCitableSourceExists() throws Exception {
        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"无来源引用约束测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"共享单车围挡备案流程依据是什么？"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.content").value(containsString("未检索到可引用的政策法规或业务依据")))
                .andExpect(jsonPath("$.data.citations.length()").value(0));
    }

    @Test
    void shouldExcludeAbolishedDocumentFromFormalKnowledgeSearch() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "abolished-policy.txt",
                MediaType.TEXT_PLAIN_VALUE,
                """
                共享单车围挡备案流程
                对共享单车围挡备案，应当先完成现场核查，再由责任单位提交备案材料。
                """.getBytes()
        );

        String uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge/documents")
                        .file(file)
                        .param("title", "共享单车围挡备案流程废止稿")
                        .param("category", "POLICY")
                        .param("sourceOrg", "市城管局")
                        .param("documentNumber", "城管废〔2026〕1号"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String documentId = uploadResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/knowledge/documents/{documentId}/index", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"));

        mockMvc.perform(post("/api/v1/knowledge/documents/{documentId}/status", documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ABOLISHED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("abolished"));

        mockMvc.perform(get("/api/v1/knowledge/search")
                        .param("query", "责任单位提交备案材料"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

    }

    @Test
    void shouldFilterExpiredAndWrongRegionDocumentsFromFormalKnowledgeSearch() throws Exception {
        MockMultipartFile expiredFile = new MockMultipartFile(
                "file",
                "expired-policy.txt",
                MediaType.TEXT_PLAIN_VALUE,
                """
                夜间餐饮油烟专项巡查要求
                柯桥区应当对夜间高值预警商户开展专项巡查。
                """.getBytes()
        );
        String expiredUploadResponse = mockMvc.perform(multipart("/api/v1/knowledge/documents")
                        .file(expiredFile)
                        .param("title", "夜间餐饮油烟专项巡查要求（过期）")
                        .param("category", "POLICY")
                        .param("sourceOrg", "柯桥区综合执法局")
                        .param("documentNumber", "柯综执〔2025〕9号")
                        .param("regionCode", "shaoxing-keqiao")
                        .param("effectiveFrom", "2025-01-01")
                        .param("effectiveTo", "2025-12-31"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String expiredDocumentId = expiredUploadResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/knowledge/documents/{documentId}/index", expiredDocumentId))
                .andExpect(status().isOk());

        MockMultipartFile wrongRegionFile = new MockMultipartFile(
                "file",
                "wrong-region-policy.txt",
                MediaType.TEXT_PLAIN_VALUE,
                """
                柯桥区共享单车停放治理要求
                对重点路口共享单车停放问题，应当纳入日常巡查清单。
                """.getBytes()
        );
        String uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge/documents")
                        .file(wrongRegionFile)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .param("title", "柯桥区共享单车停放治理要求")
                        .param("category", "POLICY")
                        .param("sourceOrg", "柯桥区综合执法局")
                        .param("documentNumber", "柯综执〔2026〕11号")
                        .param("regionCode", "shaoxing-keqiao")
                        .param("effectiveFrom", "2026-01-01"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String wrongRegionDocumentId = uploadResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/knowledge/documents/{documentId}/index", wrongRegionDocumentId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/knowledge/search")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .param("query", "共享单车停放治理要求"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/v1/knowledge/search")
                        .param("query", "夜间餐饮油烟专项巡查要求"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
