package com.example.urbanagent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:urban-agent-model-unavailable-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "urban-agent.model.provider=openai-compatible",
        "urban-agent.model.api-key="
})
class ModelUnavailableIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnUnavailableInsteadOfFallbackAnswerWhenModelIsUnavailable() throws Exception {
        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"模型不可用测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"你好"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(90001))
                .andExpect(jsonPath("$.message").value("大模型服务暂不可用，请稍后再试。"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(containsString("当前平台支持"))));
    }

    @Test
    void shouldCompleteStreamWithUnavailableEventWhenModelIsUnavailable() throws Exception {
        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"模型不可用流式测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        MvcResult result = mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/stream", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"你好"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult streamResult = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:agent.failed")))
                .andReturn();

        String streamBody = new String(streamResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(streamBody)
                .contains("大模型服务暂不可用，请稍后再试。")
                .doesNotContain("当前平台支持");

        mockMvc.perform(get("/api/v1/agent/sessions/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.messages[1].content").value("大模型服务暂不可用，请稍后再试。"));
    }
}
