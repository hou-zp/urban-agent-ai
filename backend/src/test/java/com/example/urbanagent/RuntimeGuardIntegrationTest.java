package com.example.urbanagent;

import com.example.urbanagent.agent.domain.AgentRun;
import com.example.urbanagent.agent.repository.AgentRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:urban-agent-runtime-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "urban-agent.model.provider=mock",
        "urban-agent.runtime.chat-requests-per-minute=1",
        "urban-agent.runtime.query-preview-requests-per-minute=1",
        "urban-agent.runtime.query-execute-requests-per-minute=1"
})
class RuntimeGuardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Test
    void shouldRejectWhenChatRateLimitExceeded() throws Exception {
        String sessionId = createSession("chat-limit-user", "聊天限流测试");

        mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .header("X-User-Id", "chat-limit-user")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"请介绍一下系统当前能力"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .header("X-User-Id", "chat-limit-user")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"再介绍一次系统能力"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(40006));
    }

    @Test
    void shouldRejectWhenPreviewRateLimitExceeded() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/data/query/preview")
                        .header("X-User-Id", "rate-limit-user")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询本周各街道投诉数量排行"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/data/query/preview")
                        .header("X-User-Id", "rate-limit-user")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询本周各街道投诉数量排行"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(40006));
    }

    @Test
    void shouldRejectWhenExecuteRateLimitExceeded() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk());

        String sql = """
                select street_name, sum(complaint_count) as metric_value
                from fact_complaint_order
                where report_date between '2026-04-01' and '2026-04-07'
                group by street_name
                limit 10
                """.replace("\n", " ");

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header("X-User-Id", "execute-limit-user")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询本周各街道投诉数量排行","sql":"%s"}
                                """.formatted(sql)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header("X-User-Id", "execute-limit-user")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询本周各街道投诉数量排行","sql":"%s"}
                                """.formatted(sql)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(40006));
    }

    @Test
    void shouldPreserveUserContextForStreamingRun() throws Exception {
        String sessionId = createSession("stream-officer", "流式上下文测试");

        MvcResult result = mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/stream", sessionId)
                        .header("X-User-Id", "stream-officer")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"请介绍一下系统当前能力"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk());

        AgentRun run = agentRunRepository.findTopBySessionIdOrderByCreatedAtDesc(sessionId).orElseThrow();
        assertThat(run.getUserId()).isEqualTo("stream-officer");
    }

    private String createSession(String userId, String title) throws Exception {
        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .header("X-User-Id", userId)
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
        return sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");
    }
}
