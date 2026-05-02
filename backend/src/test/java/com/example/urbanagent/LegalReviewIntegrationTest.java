package com.example.urbanagent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:urban-agent-legal-review-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class LegalReviewIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectNonLegalReviewerAccess() throws Exception {
        String reviewId = createPendingReview("越权审核测试");

        mockMvc.perform(get("/api/v1/legal-reviews")
                        .header("X-User-Id", "officer-a")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10002))
                .andExpect(jsonPath("$.message").value(containsString("仅法制审核人员或管理员可操作法制审核")));

        mockMvc.perform(post("/api/v1/legal-reviews/{reviewId}/approve", reviewId)
                        .header("X-User-Id", "officer-a")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"越权审批","reviewedAnswer":"越权审批内容"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    void shouldRejectLegalReviewWithComment() throws Exception {
        String reviewId = createPendingReview("驳回审核测试");

        mockMvc.perform(post("/api/v1/legal-reviews/{reviewId}/reject", reviewId)
                        .header("X-User-Id", "legal-user")
                        .header("X-User-Role", "LEGAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"缺少事实和适用条款，驳回"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.reviewerId").value("legal-user"))
                .andExpect(jsonPath("$.data.reviewComment").value("缺少事实和适用条款，驳回"));
    }

    @Test
    void shouldReviseLegalReviewAnswer() throws Exception {
        String reviewId = createPendingReview("修订审核测试");

        mockMvc.perform(post("/api/v1/legal-reviews/{reviewId}/revise", reviewId)
                        .header("X-User-Id", "legal-user")
                        .header("X-User-Role", "LEGAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"已修订为条件化表述","reviewedAnswer":"请先核实违法事实、证据和适用条款，再依法给出处置建议。"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVISED"))
                .andExpect(jsonPath("$.data.reviewerId").value("legal-user"))
                .andExpect(jsonPath("$.data.reviewedAnswer").value("请先核实违法事实、证据和适用条款，再依法给出处置建议。"));
    }

    @Test
    void shouldRequestMoreFactsForLegalReview() throws Exception {
        String reviewId = createPendingReview("补充事实审核测试");

        mockMvc.perform(post("/api/v1/legal-reviews/{reviewId}/request-more-facts", reviewId)
                        .header("X-User-Id", "legal-user")
                        .header("X-User-Role", "LEGAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"请补充违法事实、证据材料和现场照片。"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEED_MORE_FACTS"))
                .andExpect(jsonPath("$.data.reviewerId").value("legal-user"))
                .andExpect(jsonPath("$.data.reviewComment").value("请补充违法事实、证据材料和现场照片。"));
    }

    @Test
    void shouldRejectRepeatedLegalReviewAction() throws Exception {
        String reviewId = createPendingReview("重复审核测试");

        mockMvc.perform(post("/api/v1/legal-reviews/{reviewId}/approve", reviewId)
                        .header("X-User-Id", "legal-user")
                        .header("X-User-Role", "LEGAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"可按审查意见答复","reviewedAnswer":"请依法核实事实后处理。"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(post("/api/v1/legal-reviews/{reviewId}/reject", reviewId)
                        .header("X-User-Id", "legal-user")
                        .header("X-User-Role", "LEGAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"重复操作"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50002))
                .andExpect(jsonPath("$.message").value(containsString("不能重复操作")));
    }

    private String createPendingReview(String title) throws Exception {
        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .header("X-User-Id", "officer-a")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s"}
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");
        String messageResponse = mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .header("X-User-Id", "officer-a")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"这个能罚多少钱？"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.reviewStatus").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return messageResponse.replaceAll("(?s).*\"reviewId\":\"([^\"]+)\".*", "$1");
    }
}
