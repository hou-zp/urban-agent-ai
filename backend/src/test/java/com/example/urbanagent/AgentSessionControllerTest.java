package com.example.urbanagent;

import com.example.urbanagent.agent.domain.AgentRun;
import com.example.urbanagent.agent.application.QuestionParsingService;
import com.example.urbanagent.agent.repository.AgentRunRepository;
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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:urban-agent-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "urban-agent.model.provider=mock"
})
class AgentSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QuestionParsingService questionParsingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldExposeAgentSessionApiDocs() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Urban Agent API")))
                .andExpect(content().string(containsString("/api/v1/agent/sessions")))
                .andExpect(content().string(containsString("发送消息")));
    }

    @Test
    void shouldCreateSessionAndSendMessage() throws Exception {
        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"测试会话"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("测试会话"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"请介绍一下系统当前能力"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.role").value("ASSISTANT"));

        mockMvc.perform(get("/api/v1/agent/sessions/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(2));
    }

    @Test
    void shouldUploadIndexSearchAndReturnCitations() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                MediaType.TEXT_PLAIN_VALUE,
                """
                城市管理行政执法巡查制度
                对占道经营问题，应当按照巡查发现、现场劝导、复查闭环的流程处置。
                对群众投诉事项，应当在两个工作日内完成首次响应。
                """.getBytes()
        );

        String uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge/documents")
                        .file(file)
                        .param("title", "城市管理行政执法巡查制度")
                        .param("category", "POLICY")
                        .param("sourceOrg", "市城管局")
                        .param("documentNumber", "城管规〔2026〕8号")
                        .param("summary", "占道经营巡查和投诉响应要求"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String documentId = uploadResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/knowledge/documents/{documentId}/index", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"));

        Integer embeddingCount = jdbcTemplate.queryForObject(
                "select count(*) from knowledge_chunk_embedding where document_id = ?",
                Integer.class,
                documentId
        );
        assertThat(embeddingCount).isNotNull().isGreaterThan(0);

        mockMvc.perform(get("/api/v1/knowledge/search")
                        .param("query", "占道经营巡查流程"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].documentTitle").value("城市管理行政执法巡查制度"));

        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"知识引用测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"占道经营巡查流程是什么？"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.citations.length()").value(1))
                .andExpect(jsonPath("$.data.citations[0].documentTitle").value("城市管理行政执法巡查制度"));
    }

    @Test
    void shouldPersistParsedQuestionForRun() throws Exception {
        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"解析结果持久化测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"请根据法规说明本周柯桥区投诉数量排行，并给出处置建议"}
                                """))
                .andExpect(status().isOk());

        AgentRun run = agentRunRepository.findTopBySessionIdOrderByCreatedAtDesc(sessionId).orElseThrow();
        var parsedQuestion = questionParsingService.findByRunId(run.getId()).orElseThrow();

        assertThat(parsedQuestion.primaryIntent()).contains(com.example.urbanagent.agent.application.dto.IntentType.METRIC_QUERY);
        assertThat(parsedQuestion.hasIntent(com.example.urbanagent.agent.application.dto.IntentType.LEGAL_ADVICE)).isTrue();
        assertThat(parsedQuestion.hasIntent(com.example.urbanagent.agent.application.dto.IntentType.BUSINESS_CONSULTATION)).isTrue();
        assertThat(parsedQuestion.requiresCitation()).isTrue();
        assertThat(parsedQuestion.hasMandatoryDataIntent()).isTrue();
    }

    @Test
    void shouldAutoExecuteMixedDataAndRegulationQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk());

        MockMultipartFile lawFile = new MockMultipartFile(
                "file",
                "complaint-ranking-law.txt",
                MediaType.TEXT_PLAIN_VALUE,
                """
                城市管理投诉排行分析处置指引
                对投诉数量排行靠前的街道，应优先核查高频问题、处置时效和重复投诉来源。
                对连续居高的街道，应组织专项排查并督促属地限时整改。
                """.getBytes()
        );

        String uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge/documents")
                        .file(lawFile)
                        .param("title", "城市管理投诉排行分析处置指引")
                        .param("category", "LAW")
                        .param("sourceOrg", "市城管法制处")
                        .param("documentNumber", "城管法指〔2026〕11号")
                        .param("regionCode", "shaoxing-keqiao")
                        .param("summary", "用于投诉排行和处置建议的法规依据"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String documentId = uploadResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(post("/api/v1/knowledge/documents/{documentId}/index", documentId))
                .andExpect(status().isOk());

        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Region", "city")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"混合问题自动执行测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Region", "city")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"请根据法规说明本周柯桥区投诉数量排行，并给出处置建议"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").isNotEmpty())
                .andExpect(jsonPath("$.data.content").value(containsString("结论：")))
                .andExpect(jsonPath("$.data.content").value(containsString("建议：")))
                .andExpect(jsonPath("$.data.content").value(containsString("当前排行中")))
                .andExpect(jsonPath("$.data.citations[0].documentId").value(documentId))
                .andExpect(jsonPath("$.data.composedAnswer.queryCards[0].rows[0].STREET_NAME").exists())
                .andExpect(jsonPath("$.data.composedAnswer.queryCards[0].rows[0].METRIC_VALUE").exists());
    }

    @Test
    void shouldForceSimpleDataQuestionThroughTrustedQueryFlow() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk());

        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"可信问数回答测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"本周投诉总量"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value(containsString("结论：")))
                .andExpect(jsonPath("$.data.composedAnswer.conclusion").isNotEmpty())
                .andExpect(jsonPath("$.data.composedAnswer.dataStatements[0].queryId").isNotEmpty())
                .andExpect(jsonPath("$.data.composedAnswer.queryCards[0].queryId").isNotEmpty());

        mockMvc.perform(get("/api/v1/agent/sessions/{sessionId}", sessionId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[1].composedAnswer.dataStatements[0].queryId").isNotEmpty())
                .andExpect(jsonPath("$.data.messages[1].composedAnswer.queryCards[0].queryId").isNotEmpty());
    }

    @Test
    void shouldPreferPolicyDocumentForPolicyQuestion() throws Exception {
        MockMultipartFile policyFile = new MockMultipartFile(
                "file",
                "idle-land-policy.txt",
                MediaType.TEXT_PLAIN_VALUE,
                """
                柯桥区空闲地块治理工作口径
                空闲地块治理属于城市品质提升和市容环境治理事项，重点解决裸露黄土、垃圾堆放、渣土乱倒、乱搭乱建等问题。
                """.getBytes()
        );

        String policyUploadResponse = mockMvc.perform(multipart("/api/v1/knowledge/documents")
                        .file(policyFile)
                        .param("title", "柯桥区空闲地块治理工作口径")
                        .param("category", "POLICY")
                        .param("sourceOrg", "柯桥区综合行政执法局"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String policyDocumentId = policyUploadResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/knowledge/documents/{documentId}/index", policyDocumentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"));

        MockMultipartFile businessFile = new MockMultipartFile(
                "file",
                "idle-land-business.txt",
                MediaType.TEXT_PLAIN_VALUE,
                """
                空闲地块扩展问答
                问：空闲地块相关政策怎么答？
                答：可先说明治理对象、常见问题和属地协同要求，再结合政策口径补充解释。
                """.getBytes()
        );

        String businessUploadResponse = mockMvc.perform(multipart("/api/v1/knowledge/documents")
                        .file(businessFile)
                        .param("title", "空闲地块扩展问答")
                        .param("category", "BUSINESS")
                        .param("sourceOrg", "业务资料整理"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String businessDocumentId = businessUploadResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/knowledge/documents/{documentId}/index", businessDocumentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"));

        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"空闲地块政策优先级测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"空闲地块相关政策"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.citations[0].category").value("POLICY"))
                .andExpect(jsonPath("$.data.citations[0].documentTitle").value(org.hamcrest.Matchers.containsString("空闲地块")));
    }

    @Test
    void shouldAvoidUnrelatedLawCitationForIdleLandPolicyQuestion() throws Exception {
        MockMultipartFile idleLandLaw = new MockMultipartFile(
                "file",
                "idle-land-policy-law.txt",
                MediaType.TEXT_PLAIN_VALUE,
                """
                空闲地块政策答复要点
                要先区分城市治理中的空闲地块整治与自然资源意义上的闲置土地认定。
                前者重点回答环境整治、围挡保洁、垃圾清运、裸土覆盖和长效巡查要求。
                """.getBytes()
        );

        String idleLandLawResponse = mockMvc.perform(multipart("/api/v1/knowledge/documents")
                        .file(idleLandLaw)
                        .param("title", "空闲地块政策答复要点")
                        .param("category", "LAW")
                        .param("sourceOrg", "柯桥区自然资源协同专班"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String idleLandLawId = idleLandLawResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(post("/api/v1/knowledge/documents/{documentId}/index", idleLandLawId))
                .andExpect(status().isOk());

        MockMultipartFile complaintLaw = new MockMultipartFile(
                "file",
                "complaint-ranking-law.txt",
                MediaType.TEXT_PLAIN_VALUE,
                """
                城市管理投诉排行分析处置指引
                对投诉数量排行靠前的街道，应优先核查高频问题、处置时效和重复投诉来源。
                """.getBytes()
        );

        String complaintLawResponse = mockMvc.perform(multipart("/api/v1/knowledge/documents")
                        .file(complaintLaw)
                        .param("title", "城市管理投诉排行分析处置指引")
                        .param("category", "LAW")
                        .param("sourceOrg", "市城管法制处"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String complaintLawId = complaintLawResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(post("/api/v1/knowledge/documents/{documentId}/index", complaintLawId))
                .andExpect(status().isOk());

        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"空闲地块法律口径测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        String answerResponse = mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"空闲地块相关政策怎么答？"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.citations[0].documentTitle").value(containsString("空闲地块")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode citations = objectMapper.readTree(answerResponse).path("data").path("citations");
        assertThat(citations).allMatch(node -> !node.path("documentTitle").asText().contains("投诉排行"));
    }

    @Test
    void shouldTreatIdleLandClassificationQuestionAsPolicyQuestion() throws Exception {
        MockMultipartFile policyFile = new MockMultipartFile(
                "file",
                "idle-land-classification.txt",
                MediaType.TEXT_PLAIN_VALUE,
                """
                柯桥区空闲地块分类补充口径
                空闲地块可分为常态管理类、整治提升类、临时利用类、法定认定衔接类。
                """.getBytes()
        );

        String uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge/documents")
                        .file(policyFile)
                        .param("title", "柯桥区空闲地块分类补充口径")
                        .param("category", "POLICY")
                        .param("sourceOrg", "柯桥区综合行政执法局"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String documentId = uploadResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/knowledge/documents/{documentId}/index", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"));

        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"空闲地块分类测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"空闲地块分为哪几类？比如常态管理类。"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.citations[0].documentTitle").value("柯桥区空闲地块分类补充口径"));
    }

    @Test
    void shouldSyncCatalogAndFilterAuthorizedFields() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataSourceCount").value(1))
                .andExpect(jsonPath("$.data.metricCount").value(9));

        mockMvc.perform(get("/api/v1/data/catalog/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(9))
                .andExpect(jsonPath("$.data[0].metricCode").exists())
                .andExpect(jsonPath("$.data[0].caliberVersion").isNotEmpty())
                .andExpect(jsonPath("$.data[0].dataQuality").isNotEmpty())
                .andExpect(jsonPath("$.data[0].applicableRegion").isNotEmpty())
                .andExpect(jsonPath("$.data[0].dataUpdatedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/data/catalog/tables")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].fields[*].fieldName").isArray())
                .andExpect(content().string(not(containsString("reporter_phone"))));
    }

    @Test
    void shouldPreviewAndExecuteReadonlyQuery() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk());

        String previewResponse = mockMvc.perform(post("/api/v1/data/query/preview")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询本周各街道投诉数量排行"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metricCode").value("complaint_count"))
                .andExpect(jsonPath("$.data.validatedSql").value(org.hamcrest.Matchers.containsString("region_code = 'district-a'")))
                .andExpect(jsonPath("$.data.dataStatement.caliberVersion").value("CITY-COMPLAINT-2026.01"))
                .andExpect(jsonPath("$.data.dataStatement.dataUpdatedAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sql = previewResponse.replaceAll("(?s).*\"validatedSql\":\"([^\"]+)\".*", "$1")
                .replace("\\\"", "\"");

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询本周各街道投诉数量排行","sql":"%s"}
                                """.formatted(sql.replace("\"", "\\\""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowCount").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.dataStatement.caliberVersion").value("CITY-COMPLAINT-2026.01"))
                .andExpect(jsonPath("$.data.rows[0].STREET_NAME").exists())
                .andExpect(jsonPath("$.data.rows[0].METRIC_VALUE").exists());
    }

    @Test
    void shouldExplainOilFumeThresholdQuestionClearly() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/data/query/preview")
                        .header("X-User-Role", "MANAGER")
                        .header("X-User-Region", "shaoxing-keqiao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"请问柯桥区当前的油烟浓度超标阀值是多少，与以前相比有什么变化，当前还有多少油烟浓度超标的预警未闭环。"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("当前暂未配置“油烟超标阈值”指标")))
                .andExpect(jsonPath("$.message").value(containsString("建议拆开提问")));
    }

    @Test
    void shouldRouteHighRiskQuestionToLegalReview() throws Exception {
        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"风险审核测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        String messageResponse = mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .header("X-User-Role", "OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"这个能罚多少钱？"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.reviewStatus").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String reviewId = messageResponse.replaceAll("(?s).*\"reviewId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/legal-reviews")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));

        mockMvc.perform(post("/api/v1/legal-reviews/{reviewId}/approve", reviewId)
                        .header("X-User-Id", "legal-user")
                        .header("X-User-Role", "LEGAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"补充事实后可按法规答复","reviewedAnswer":"请依据具体违法事实、适用条款和裁量基准确定处罚金额。"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewerId").value("legal-user"));
    }

    @Test
    void shouldExposeAuditApis() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/data/query/preview")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询本周各街道投诉数量排行"}
                                """))
                .andExpect(status().isOk());

        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"审计查询测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"这个能罚多少钱？"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"请介绍系统当前能力"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit/agent-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/api/v1/audit/tool-calls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/api/v1/audit/data-access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/api/v1/audit/risk-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data[0].riskLevel").value("HIGH"));

        mockMvc.perform(get("/api/v1/audit/model-calls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data[0].provider").value("mock"))
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"));
    }

    @Test
    void shouldCreateAndQueryPlanForComplexAnalysisTask() throws Exception {
        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"综合分析测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        String messageResponse = mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"请综合分析本周各街道投诉趋势并形成周报摘要"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.runId").isNotEmpty())
                .andExpect(jsonPath("$.data.planId").isNotEmpty())
                .andExpect(jsonPath("$.data.content").value(containsString("已生成执行计划")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String runId = messageResponse.replaceAll("(?s).*\"runId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/agent/sessions/runs/{runId}/plan", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(runId))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.systemSummary.autorunCount").value(0))
                .andExpect(jsonPath("$.data.systemSummary.recoverCount").value(0))
                .andExpect(jsonPath("$.data.systemSummary.affectedStepCount").value(0))
                .andExpect(jsonPath("$.data.steps.length()").value(4))
                .andExpect(jsonPath("$.data.steps[0].name").value("问题解析"))
                .andExpect(jsonPath("$.data.steps[0].taskType").value("QUESTION_ANALYSIS"))
                .andExpect(jsonPath("$.data.steps[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[1].name").value("数据查询准备"))
                .andExpect(jsonPath("$.data.steps[1].taskType").value("DATA_QUERY_PREPARE"))
                .andExpect(jsonPath("$.data.steps[1].status").value("TODO"))
                .andExpect(jsonPath("$.data.steps[2].name").value("数据查询执行"))
                .andExpect(jsonPath("$.data.steps[2].taskType").value("DATA_QUERY_EXECUTE"))
                .andExpect(jsonPath("$.data.steps[3].name").value("答案生成"))
                .andExpect(jsonPath("$.data.steps[3].taskType").value("ANSWER_COMPOSE"));
    }

    @Test
    void shouldExecutePlanStepsForDataAnalysisTask() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk());

        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"计划执行测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        String messageResponse = mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"请综合分析本周各街道投诉数量排行并形成周报摘要"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String runId = messageResponse.replaceAll("(?s).*\"runId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/execute-next", runId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.steps[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[1].outputSummary").value(containsString("已生成候选 SQL")))
                .andExpect(jsonPath("$.data.steps[1].outputPayload.kind").value("query-preview"))
                .andExpect(jsonPath("$.data.steps[1].outputPayload.queryId").isNotEmpty())
                .andExpect(jsonPath("$.data.steps[1].outputPayload.validatedSql").isNotEmpty())
                .andExpect(jsonPath("$.data.steps[2].status").value("TODO"));

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/execute-next", runId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.steps[2].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[2].outputSummary").value(containsString("返回")))
                .andExpect(jsonPath("$.data.steps[2].outputPayload.kind").value("query-execute"))
                .andExpect(jsonPath("$.data.steps[2].outputPayload.queryId").isNotEmpty())
                .andExpect(jsonPath("$.data.steps[2].outputPayload.executedSql").isNotEmpty());

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/execute-next", runId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[3].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[3].outputSummary").isNotEmpty());

        mockMvc.perform(get("/api/v1/agent/sessions/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(3))
                .andExpect(jsonPath("$.data.messages[2].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.messages[2].content").value(containsString("结论：")))
                .andExpect(jsonPath("$.data.messages[2].composedAnswer.conclusion").isNotEmpty())
                .andExpect(jsonPath("$.data.messages[2].composedAnswer.dataStatements[0].queryId").isNotEmpty())
                .andExpect(jsonPath("$.data.messages[2].composedAnswer.queryCards[0].queryId").isNotEmpty());
    }

    @Test
    void shouldBuildPlanGraphForMixedDataAndKnowledgeTask() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk());

        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"任务图测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        String messageResponse = mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"请依据法规综合分析本周各街道投诉数量排行并给出处置建议"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String runId = messageResponse.replaceAll("(?s).*\"runId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/agent/sessions/runs/{runId}/plan", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps.length()").value(5))
                .andExpect(jsonPath("$.data.steps[3].taskType").value("KNOWLEDGE_RETRIEVE"))
                .andExpect(jsonPath("$.data.steps[3].dependencyStepIds").value("1"))
                .andExpect(jsonPath("$.data.steps[4].taskType").value("ANSWER_COMPOSE"))
                .andExpect(jsonPath("$.data.steps[4].dependencyStepIds").value("3,4"));
    }

    @Test
    void shouldExecuteExplicitPlanStepIdempotently() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk());

        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"计划步骤幂等测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        String messageResponse = mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"请综合分析本周各街道投诉数量排行并形成周报摘要"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String runId = messageResponse.replaceAll("(?s).*\"runId\":\"([^\"]+)\".*", "$1");
        String dataStepId = getPlanStepId(runId, 1);

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute", runId, dataStepId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.steps[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[2].status").value("TODO"));

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute", runId, dataStepId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.steps[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[2].status").value("TODO"));
    }

    @Test
    void shouldExecuteDependentPlanStepUsingPersistedArtifact() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk());

        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"计划上下文测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        String messageResponse = mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"请综合分析本周各街道投诉数量排行并形成周报摘要"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String runId = messageResponse.replaceAll("(?s).*\"runId\":\"([^\"]+)\".*", "$1");
        String prepareStepId = getPlanStepId(runId, 1);
        String executeStepId = getPlanStepId(runId, 2);

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute", runId, prepareStepId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[1].outputPayload.kind").value("query-preview"))
                .andExpect(jsonPath("$.data.steps[1].outputPayload.validatedSql").isNotEmpty());

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute", runId, executeStepId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[2].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[2].outputPayload.kind").value("query-execute"))
                .andExpect(jsonPath("$.data.steps[2].outputPayload.queryId").isNotEmpty())
                .andExpect(jsonPath("$.data.steps[2].outputPayload.executedSql").isNotEmpty());
    }

    @Test
    void shouldComposeAnswerUsingPersistedKnowledgeArtifact() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk());

        MockMultipartFile policyFile = new MockMultipartFile(
                "file",
                "complaint-policy.txt",
                MediaType.TEXT_PLAIN_VALUE,
                """
                城市管理投诉分析法规指引
                对本周各街道投诉数量排行分析，应结合投诉数量、处置时效、重复投诉和责任划分形成处置建议。
                开展街道投诉排行研判时，应优先引用法规依据和处置规范。
                """.getBytes()
        );

        String uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge/documents")
                        .file(policyFile)
                        .param("title", "城市管理投诉分析法规指引")
                        .param("category", "LAW")
                        .param("sourceOrg", "市城管局")
                        .param("summary", "本周各街道投诉数量排行分析应结合法规依据、处置时效和责任划分"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String documentId = uploadResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/knowledge/documents/{documentId}/index", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"));

        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"知识产物复用测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        String messageResponse = mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"请依据法规综合分析本周各街道投诉数量排行并给出处置建议"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String runId = messageResponse.replaceAll("(?s).*\"runId\":\"([^\"]+)\".*", "$1");
        String prepareStepId = getPlanStepId(runId, 1);
        String executeStepId = getPlanStepId(runId, 2);
        String knowledgeStepId = getPlanStepId(runId, 3);
        String answerStepId = getPlanStepId(runId, 4);

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute", runId, prepareStepId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute", runId, executeStepId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute", runId, knowledgeStepId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[3].outputPayload.kind").value("knowledge-hits"))
                .andExpect(jsonPath("$.data.steps[3].outputPayload.documentIds[0]").value(documentId));

        jdbcTemplate.update("delete from knowledge_chunk_embedding where document_id = ?", documentId);
        jdbcTemplate.update("delete from knowledge_chunk where document_id = ?", documentId);

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute", runId, answerStepId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[4].status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/agent/sessions/{sessionId}", sessionId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[2].citations[0].documentId").value(documentId))
                .andExpect(jsonPath("$.data.messages[2].citations[0].documentTitle").value("城市管理投诉分析法规指引"))
                .andExpect(jsonPath("$.data.messages[2].composedAnswer.evidenceRefs[0].documentId").value(documentId));
    }

    @Test
    void shouldRetryFailedExplicitPlanStepAfterPermissionChange() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk());

        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"计划失败重试测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        String messageResponse = mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"请综合分析本周超期案件数量并形成月报摘要"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String runId = messageResponse.replaceAll("(?s).*\"runId\":\"([^\"]+)\".*", "$1");
        String dataStepId = getPlanStepId(runId, 1);

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute", runId, dataStepId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.steps[1].status").value("FAILED"))
                .andExpect(jsonPath("$.data.steps[1].outputSummary").value(containsString("字段无访问权限")))
                .andExpect(jsonPath("$.data.steps[1].failureDetail.errorCode").value("SQL_PERMISSION_DENIED"))
                .andExpect(jsonPath("$.data.steps[1].failureDetail.category").value("EXECUTION_FAILED"))
                .andExpect(jsonPath("$.data.steps[1].failureDetail.headline").value("当前步骤需要更高的数据权限"))
                .andExpect(jsonPath("$.data.steps[1].failureDetail.action").value("RETRY_CURRENT"))
                .andExpect(jsonPath("$.data.steps[1].failureDetail.actionLabel").value("调整权限后重试"))
                .andExpect(jsonPath("$.data.steps[1].failureDetail.handleCode").value("SWITCH_ROLE"))
                .andExpect(jsonPath("$.data.steps[1].failureDetail.dependencyBlocked").value(false))
                .andExpect(jsonPath("$.data.steps[1].failureDetail.reason").value(containsString("无访问权限")))
                .andExpect(jsonPath("$.data.steps[1].retryAdvice.action").value("RETRY_CURRENT"))
                .andExpect(jsonPath("$.data.steps[1].retryAdvice.reason").value(containsString("直接重试")));

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute", runId, dataStepId)
                        .header("X-User-Role", "MANAGER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.steps[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[2].status").value("TODO"));
    }

    @Test
    void shouldAutoRecoverPreparedArtifactWhenExecutingDependentStep() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk());

        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"计划依赖重建建议测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        String messageResponse = mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"请综合分析本周各街道投诉数量排行并形成周报摘要"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String runId = messageResponse.replaceAll("(?s).*\"runId\":\"([^\"]+)\".*", "$1");
        String prepareStepId = getPlanStepId(runId, 1);
        String executeStepId = getPlanStepId(runId, 2);

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute", runId, prepareStepId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[1].status").value("COMPLETED"));

        jdbcTemplate.update("update plan_step set output_payload_json = null where id = ?", prepareStepId);

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute", runId, executeStepId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.steps[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[1].outputPayload.kind").value("query-preview"))
                .andExpect(jsonPath("$.data.steps[1].outputPayload.validatedSql").isNotEmpty())
                .andExpect(jsonPath("$.data.steps[2].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[2].outputPayload.kind").value("query-execute"))
                .andExpect(jsonPath("$.data.steps[2].outputPayload.queryId").isNotEmpty());
    }

    @Test
    void shouldAutoRecoverAnswerDependenciesBeforeCompose() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk());

        MockMultipartFile policyFile = new MockMultipartFile(
                "file",
                "complaint-policy.txt",
                MediaType.TEXT_PLAIN_VALUE,
                """
                城市管理投诉分析法规指引
                对本周各街道投诉数量排行分析，应结合投诉数量、处置时效、重复投诉和责任划分形成处置建议。
                开展街道投诉排行研判时，应优先引用法规依据和处置规范。
                """.getBytes()
        );

        String uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge/documents")
                        .file(policyFile)
                        .param("title", "城市管理投诉分析法规指引")
                        .param("category", "LAW")
                        .param("sourceOrg", "市城管局")
                        .param("summary", "本周各街道投诉数量排行分析应结合法规依据、处置时效和责任划分"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String documentId = uploadResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/knowledge/documents/{documentId}/index", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"));

        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"答案汇总自动恢复测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        String messageResponse = mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"请依据法规综合分析本周各街道投诉数量排行并给出处置建议"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String runId = messageResponse.replaceAll("(?s).*\"runId\":\"([^\"]+)\".*", "$1");
        String prepareStepId = getPlanStepId(runId, 1);
        String executeStepId = getPlanStepId(runId, 2);
        String knowledgeStepId = getPlanStepId(runId, 3);
        String answerStepId = getPlanStepId(runId, 4);

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute", runId, prepareStepId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute", runId, executeStepId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute", runId, knowledgeStepId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk());

        jdbcTemplate.update("update plan_step set output_payload_json = null where id = ?", prepareStepId);
        jdbcTemplate.update("update plan_step set output_payload_json = null, result_ref = null where id = ?", executeStepId);
        jdbcTemplate.update("update plan_step set output_payload_json = null where id = ?", knowledgeStepId);

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute", runId, answerStepId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.systemSummary.autorunCount").value(0))
                .andExpect(jsonPath("$.data.systemSummary.recoverCount").value(3))
                .andExpect(jsonPath("$.data.systemSummary.affectedStepCount").value(2))
                .andExpect(jsonPath("$.data.systemSummary.summary").value(containsString("自动重建 3 次")))
                .andExpect(jsonPath("$.data.steps[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[1].outputPayload.kind").value("query-preview"))
                .andExpect(jsonPath("$.data.steps[1].outputPayload.validatedSql").isNotEmpty())
                .andExpect(jsonPath("$.data.steps[1].executionTrace.triggerMode").value("USER"))
                .andExpect(jsonPath("$.data.steps[1].executionTrace.triggerLabel").value("用户执行"))
                .andExpect(jsonPath("$.data.steps[1].executionTrace.resultLabel").value("查询预览"))
                .andExpect(jsonPath("$.data.steps[2].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[2].outputPayload.kind").value("query-execute"))
                .andExpect(jsonPath("$.data.steps[2].outputPayload.queryId").isNotEmpty())
                .andExpect(jsonPath("$.data.steps[2].systemActions.length()").value(1))
                .andExpect(jsonPath("$.data.steps[2].systemActions[0].action").value("RECOVER"))
                .andExpect(jsonPath("$.data.steps[2].systemActions[0].dependencyStepOrder").value(2))
                .andExpect(jsonPath("$.data.steps[2].executionTrace.triggerMode").value("SYSTEM_RECOVER"))
                .andExpect(jsonPath("$.data.steps[2].executionTrace.triggerLabel").value("系统重建"))
                .andExpect(jsonPath("$.data.steps[2].executionTrace.systemActionCount").value(1))
                .andExpect(jsonPath("$.data.steps[2].executionTrace.resultLabel").value("查询结果"))
                .andExpect(jsonPath("$.data.steps[3].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[3].outputPayload.kind").value("knowledge-hits"))
                .andExpect(jsonPath("$.data.steps[3].outputPayload.documentIds", org.hamcrest.Matchers.hasItem(documentId)))
                .andExpect(jsonPath("$.data.steps[4].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[4].outputPayload.kind").value("answer-compose"))
                .andExpect(jsonPath("$.data.steps[4].systemActions.length()").value(2))
                .andExpect(jsonPath("$.data.steps[4].systemActions[*].action", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("RECOVER"))))
                .andExpect(jsonPath("$.data.steps[4].systemActions[*].dependencyStepOrder", org.hamcrest.Matchers.containsInAnyOrder(3, 4)))
                .andExpect(jsonPath("$.data.steps[4].executionTrace.triggerMode").value("SYSTEM_RECOVER"))
                .andExpect(jsonPath("$.data.steps[4].executionTrace.systemActionCount").value(2))
                .andExpect(jsonPath("$.data.steps[4].executionTrace.resultLabel").value("答案汇总"));

        mockMvc.perform(get("/api/v1/agent/sessions/{sessionId}", sessionId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[2].content").value(containsString("结论：")))
                .andExpect(jsonPath("$.data.messages[2].citations[*].documentId", org.hamcrest.Matchers.hasItem(documentId)))
                .andExpect(jsonPath("$.data.messages[2].composedAnswer.dataStatements[0].queryId").isNotEmpty())
                .andExpect(jsonPath("$.data.messages[2].composedAnswer.queryCards[0].queryId").isNotEmpty())
                .andExpect(jsonPath("$.data.messages[2].composedAnswer.evidenceRefs[*].documentId", org.hamcrest.Matchers.hasItem(documentId)));

        Integer recoveryCount = jdbcTemplate.queryForObject(
                "select count(*) from tool_call where run_id = ? and tool_name = 'plan.dependency_recover'",
                Integer.class,
                runId
        );
        assertThat(recoveryCount).isEqualTo(3);

        String recoverySummary = String.join("\n", jdbcTemplate.queryForList(
                "select output_summary from tool_call where run_id = ? and tool_name = 'plan.dependency_recover' order by created_at asc",
                String.class,
                runId
        ));
        assertThat(recoverySummary).contains("前置步骤产物缺失");
    }

    @Test
    void shouldAutoRunTodoDependenciesWhenExecutingAnswerStep() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk());

        MockMultipartFile policyFile = new MockMultipartFile(
                "file",
                "complaint-policy.txt",
                MediaType.TEXT_PLAIN_VALUE,
                """
                城市管理投诉分析法规指引
                对本周各街道投诉数量排行分析，应结合投诉数量、处置时效、重复投诉和责任划分形成处置建议。
                开展街道投诉排行研判时，应优先引用法规依据和处置规范。
                """.getBytes()
        );

        String uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge/documents")
                        .file(policyFile)
                        .param("title", "城市管理投诉分析法规指引")
                        .param("category", "LAW")
                        .param("sourceOrg", "市城管局")
                        .param("summary", "本周各街道投诉数量排行分析应结合法规依据、处置时效和责任划分"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String documentId = uploadResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/knowledge/documents/{documentId}/index", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"));

        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"直接执行答案步骤测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        String messageResponse = mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/messages", sessionId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"请依据法规综合分析本周各街道投诉数量排行并给出处置建议"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String runId = messageResponse.replaceAll("(?s).*\"runId\":\"([^\"]+)\".*", "$1");
        String answerStepId = getPlanStepId(runId, 4);

        mockMvc.perform(post("/api/v1/agent/sessions/runs/{runId}/plan/steps/{stepId}/execute", runId, answerStepId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.systemSummary.autorunCount").value(3))
                .andExpect(jsonPath("$.data.systemSummary.recoverCount").value(0))
                .andExpect(jsonPath("$.data.systemSummary.affectedStepCount").value(2))
                .andExpect(jsonPath("$.data.systemSummary.summary").value(containsString("自动补跑 3 次")))
                .andExpect(jsonPath("$.data.steps[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[1].outputPayload.kind").value("query-preview"))
                .andExpect(jsonPath("$.data.steps[1].executionTrace.triggerMode").value("USER"))
                .andExpect(jsonPath("$.data.steps[2].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[2].outputPayload.kind").value("query-execute"))
                .andExpect(jsonPath("$.data.steps[2].systemActions.length()").value(1))
                .andExpect(jsonPath("$.data.steps[2].systemActions[0].action").value("AUTORUN"))
                .andExpect(jsonPath("$.data.steps[2].systemActions[0].dependencyStepOrder").value(2))
                .andExpect(jsonPath("$.data.steps[2].executionTrace.triggerMode").value("SYSTEM_AUTORUN"))
                .andExpect(jsonPath("$.data.steps[2].executionTrace.systemActionCount").value(1))
                .andExpect(jsonPath("$.data.steps[3].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[3].outputPayload.kind").value("knowledge-hits"))
                .andExpect(jsonPath("$.data.steps[3].outputPayload.documentIds", org.hamcrest.Matchers.hasItem(documentId)))
                .andExpect(jsonPath("$.data.steps[4].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[4].outputPayload.kind").value("answer-compose"))
                .andExpect(jsonPath("$.data.steps[4].systemActions.length()").value(2))
                .andExpect(jsonPath("$.data.steps[4].systemActions[*].action", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("AUTORUN"))))
                .andExpect(jsonPath("$.data.steps[4].systemActions[*].dependencyStepOrder", org.hamcrest.Matchers.containsInAnyOrder(3, 4)))
                .andExpect(jsonPath("$.data.steps[4].executionTrace.triggerMode").value("SYSTEM_AUTORUN"))
                .andExpect(jsonPath("$.data.steps[4].executionTrace.systemActionCount").value(2))
                .andExpect(jsonPath("$.data.steps[4].executionTrace.resultLabel").value("答案汇总"));

        mockMvc.perform(get("/api/v1/agent/sessions/{sessionId}", sessionId)
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[2].content").value(containsString("结论：")))
                .andExpect(jsonPath("$.data.messages[2].citations[*].documentId", org.hamcrest.Matchers.hasItem(documentId)))
                .andExpect(jsonPath("$.data.messages[2].composedAnswer.dataStatements[0].queryId").isNotEmpty())
                .andExpect(jsonPath("$.data.messages[2].composedAnswer.evidenceRefs[*].documentId", org.hamcrest.Matchers.hasItem(documentId)));

        Integer autorunCount = jdbcTemplate.queryForObject(
                "select count(*) from tool_call where run_id = ? and tool_name = 'plan.dependency_autorun'",
                Integer.class,
                runId
        );
        assertThat(autorunCount).isEqualTo(3);

        String autorunInput = String.join("\n", jdbcTemplate.queryForList(
                "select input_summary from tool_call where run_id = ? and tool_name = 'plan.dependency_autorun' order by created_at asc",
                String.class,
                runId
        ));
        assertThat(autorunInput).contains("triggerStepOrder=5")
                .contains("triggerStepOrder=3")
                .contains("dependencyStepOrder=2")
                .contains("dependencyStepOrder=3");
    }

    @Test
    void shouldCancelAndResumeRun() throws Exception {
        String sessionResponse = mockMvc.perform(post("/api/v1/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"取消恢复测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = sessionResponse.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");
        AgentRun run = agentRunRepository.save(new AgentRun(sessionId, "demo-user", "请继续分析本周投诉情况", "urban-management-agent"));

        mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/cancel", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(run.getId()))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/agent/sessions/runs/{runId}", run.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/agent/sessions/{sessionId}/resume", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.content").exists());
    }

    private String getPlanStepId(String runId, int stepIndex) throws Exception {
        String planResponse = mockMvc.perform(get("/api/v1/agent/sessions/runs/{runId}/plan", runId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode steps = objectMapper.readTree(planResponse).path("data").path("steps");
        return steps.get(stepIndex).path("id").asText();
    }
}
