package com.example.urbanagent;

import com.example.urbanagent.common.async.AiEvaluationTaskPayload;
import com.example.urbanagent.common.async.AsyncTaskDispatchService;
import com.example.urbanagent.common.async.AsyncTaskEvent;
import com.example.urbanagent.common.async.AsyncTaskEventType;
import com.example.urbanagent.common.async.InMemoryAsyncTaskEventPublisher;
import com.example.urbanagent.common.async.KnowledgeDocumentIndexTaskPayload;
import com.example.urbanagent.common.async.KnowledgeIndexRebuildTaskPayload;
import com.example.urbanagent.common.async.KnowledgeQualityCheckTaskPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:urban-agent-async-task-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "urban-agent.model.provider=mock",
        "urban-agent.async.publisher=memory"
})
class AsyncTaskDispatchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryAsyncTaskEventPublisher publisher;

    @Autowired
    private AsyncTaskDispatchService asyncTaskDispatchService;

    @BeforeEach
    void setUp() {
        publisher.clear();
    }

    @Test
    void shouldPublishKnowledgeDocumentIndexEventWhenDocumentUploaded() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "async-policy.txt",
                MediaType.TEXT_PLAIN_VALUE,
                """
                城市管理知识文档
                对重点区域应当开展日常巡查并留痕。
                """.getBytes()
        );

        String response = mockMvc.perform(multipart("/api/v1/knowledge/documents")
                        .file(file)
                        .param("title", "异步索引测试文档")
                        .param("category", "POLICY")
                        .param("sourceOrg", "市城管局")
                        .param("documentNumber", "城管规〔2026〕318号")
                        .param("regionCode", "district-a")
                        .param("summary", "异步投递测试"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        String documentId = jsonNode.path("data").path("id").asText();

        List<AsyncTaskEvent<? extends com.example.urbanagent.common.async.AsyncTaskPayload>> events = publisher.publishedEvents();
        assertThat(events).hasSize(1);
        AsyncTaskEvent<? extends com.example.urbanagent.common.async.AsyncTaskPayload> event = events.get(0);
        assertThat(event.type()).isEqualTo(AsyncTaskEventType.KNOWLEDGE_DOCUMENT_INDEX_REQUESTED);
        assertThat(event.resourceType()).isEqualTo("knowledge_document");
        assertThat(event.resourceId()).isEqualTo(documentId);
        assertThat(event.routingKey()).isEqualTo("knowledge.document.index.requested");
        assertThat(event.payload()).isInstanceOf(KnowledgeDocumentIndexTaskPayload.class);
        KnowledgeDocumentIndexTaskPayload payload = (KnowledgeDocumentIndexTaskPayload) event.payload();
        assertThat(payload.title()).isEqualTo("异步索引测试文档");
        assertThat(payload.documentNumber()).isEqualTo("城管规〔2026〕318号");
        assertThat(payload.regionCode()).isEqualTo("district-a");
    }

    @Test
    void shouldSupportKnowledgeQualityRebuildAndEvaluationEvents() {
        asyncTaskDispatchService.publishKnowledgeIndexRebuildRequested("POLICY", "district-a", "nightly-rebuild");
        asyncTaskDispatchService.publishKnowledgeQualityCheckRequested("doc-1", "INTERNAL", "district-a", "daily-quality-check");
        asyncTaskDispatchService.publishAiEvaluationRequested("answer-grounding", "knowledge-citation", "run-2026-05-02", 50, "weekly-eval");

        List<AsyncTaskEvent<? extends com.example.urbanagent.common.async.AsyncTaskPayload>> events = publisher.publishedEvents();
        assertThat(events).hasSize(3);

        assertThat(events.get(0).type()).isEqualTo(AsyncTaskEventType.KNOWLEDGE_INDEX_REBUILD_REQUESTED);
        assertThat(events.get(0).payload()).isInstanceOf(KnowledgeIndexRebuildTaskPayload.class);
        assertThat(((KnowledgeIndexRebuildTaskPayload) events.get(0).payload()).triggerReason()).isEqualTo("nightly-rebuild");

        assertThat(events.get(1).type()).isEqualTo(AsyncTaskEventType.KNOWLEDGE_QUALITY_CHECK_REQUESTED);
        assertThat(events.get(1).payload()).isInstanceOf(KnowledgeQualityCheckTaskPayload.class);
        assertThat(((KnowledgeQualityCheckTaskPayload) events.get(1).payload()).documentId()).isEqualTo("doc-1");

        assertThat(events.get(2).type()).isEqualTo(AsyncTaskEventType.AI_EVALUATION_REQUESTED);
        assertThat(events.get(2).payload()).isInstanceOf(AiEvaluationTaskPayload.class);
        assertThat(((AiEvaluationTaskPayload) events.get(2).payload()).sampleSize()).isEqualTo(50);
    }
}
