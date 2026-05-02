package com.example.urbanagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:urban-agent-query-scenario-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class QueryScenarioIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc.perform(post("/api/v1/data/catalog/sync"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldPreviewAndExecuteComplaintStreetRanking() throws Exception {
        String sql = previewSql(
                "查询本周各街道投诉数量排行",
                "OFFICER",
                "district-a",
                "complaint_count",
                "fact_complaint_order",
                "region_code = 'district-a'"
        );

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "查询本周各街道投诉数量排行",
                                "sql", sql
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.executedSql").value(containsString("region_code = 'district-a'")))
                .andExpect(jsonPath("$.data.dataStatement.caliberVersion").value("CITY-COMPLAINT-2026.01"))
                .andExpect(jsonPath("$.data.rows[0].STREET_NAME").exists())
                .andExpect(jsonPath("$.data.rows[0].METRIC_VALUE").exists());
    }

    @Test
    void shouldPreviewAndExecuteInspectionProblemGridRanking() throws Exception {
        String sql = previewSql(
                "查询本周各网格巡查问题数量排行",
                "OFFICER",
                "district-a",
                "inspection_problem_count",
                "fact_inspection_record",
                "region_code = 'district-a'"
        );

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "查询本周各网格巡查问题数量排行",
                                "sql", sql
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.executedSql").value(containsString("region_code = 'district-a'")))
                .andExpect(jsonPath("$.data.rows[0].GRID_NAME").exists())
                .andExpect(jsonPath("$.data.rows[0].METRIC_VALUE").exists());
    }

    @Test
    void shouldPreviewAndExecuteOverdueCaseStatusSummaryForManager() throws Exception {
        String sql = previewSql(
                "查询本周各状态超期案件数量",
                "MANAGER",
                "district-a",
                "overdue_case_count",
                "fact_case_handle",
                "region_code = 'district-a'"
        );

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header("X-User-Role", "MANAGER")
                        .header("X-User-Region", "district-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "查询本周各状态超期案件数量",
                                "sql", sql
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.executedSql").value(containsString("region_code = 'district-a'")))
                .andExpect(jsonPath("$.data.rows[0].CASE_STATUS").exists())
                .andExpect(jsonPath("$.data.rows[0].METRIC_VALUE").exists());
    }

    @Test
    void shouldPreviewAndExecuteKeqiaoOilFumeWarningCount() throws Exception {
        String sql = previewSql(
                "请统计柯桥区当前油烟浓度超标预警数量",
                "OFFICER",
                "shaoxing-keqiao",
                "oil_fume_warning_count",
                "fact_oil_fume_warning",
                "region_code = 'shaoxing-keqiao'"
        );

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header("X-User-Role", "OFFICER")
                        .header("X-User-Region", "shaoxing-keqiao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "请统计柯桥区当前油烟浓度超标预警数量",
                                "sql", sql
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.executedSql").value(containsString("region_code = 'shaoxing-keqiao'")))
                .andExpect(jsonPath("$.data.rows[0].METRIC_VALUE").exists());
    }

    @Test
    void shouldPreviewAndExecuteKeqiaoComplaintRankingFromMixedQuestion() throws Exception {
        String sql = previewSql(
                "请根据法规说明本周柯桥区投诉数量排行，并给出处置建议",
                "ADMIN",
                "city",
                "complaint_count",
                "fact_complaint_order",
                "GROUP BY street_code"
        );

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Region", "city")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "请根据法规说明本周柯桥区投诉数量排行，并给出处置建议",
                                "sql", sql
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowCount").value(greaterThan(1)))
                .andExpect(jsonPath("$.data.rows[0].STREET_NAME").exists())
                .andExpect(jsonPath("$.data.rows[0].METRIC_VALUE").exists());
    }

    @Test
    void shouldPreviewAndExecuteKeqiaoComplaintDistributionByStreet() throws Exception {
        String sql = previewSql(
                "本周柯桥区投诉数量分布",
                "ADMIN",
                "city",
                "complaint_count",
                "fact_complaint_order",
                "GROUP BY street_code"
        );

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Region", "city")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "本周柯桥区投诉数量分布",
                                "sql", sql
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowCount").value(greaterThan(1)))
                .andExpect(jsonPath("$.data.rows[0].STREET_NAME").exists())
                .andExpect(jsonPath("$.data.rows[0].METRIC_VALUE").exists());
    }

    @Test
    void shouldKeepKeqiaoComplaintTotalAsSingleMetric() throws Exception {
        String sql = previewSql(
                "本周柯桥区投诉数量",
                "ADMIN",
                "city",
                "complaint_count",
                "fact_complaint_order",
                "fact_complaint_order"
        );

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Region", "city")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "本周柯桥区投诉数量",
                                "sql", sql
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].STREET_NAME").doesNotExist())
                .andExpect(jsonPath("$.data.rows[0].METRIC_VALUE").exists());
    }

    @Test
    void shouldAnswerMixedComplaintDistributionWithQueryCardRows() throws Exception {
        mockMvc.perform(post("/api/v1/data/query/answer")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Region", "city")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "请根据法规说明本周柯桥区投诉数量分布，并给出处置建议"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("single"))
                .andExpect(jsonPath("$.data.queryCards[0].metricCode").value("complaint_count"))
                .andExpect(jsonPath("$.data.queryCards[0].rowCount").value(greaterThan(1)))
                .andExpect(jsonPath("$.data.queryCards[0].rows[0].STREET_NAME").exists())
                .andExpect(jsonPath("$.data.queryCards[0].rows[0].METRIC_VALUE").exists());
    }

    @Test
    void shouldPreferUnclosedOilFumeWarningMetric() throws Exception {
        String sql = previewSql(
                "当前还有多少油烟浓度超标的预警未闭环",
                "MANAGER",
                "shaoxing-keqiao",
                "oil_fume_unclosed_warning_count",
                "fact_oil_fume_warning",
                "region_code = 'shaoxing-keqiao'"
        );

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header("X-User-Role", "MANAGER")
                        .header("X-User-Region", "shaoxing-keqiao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "当前还有多少油烟浓度超标的预警未闭环",
                                "sql", sql
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.rows[0].METRIC_VALUE").exists());
    }

    @Test
    void shouldGroupKeqiaoOilFumeWarningByTypeForBusinessChart() throws Exception {
        String sql = previewSql(
                "请帮我统计2026年1月的油烟预警情况，按照预警类型生成饼图，并做简单分析",
                "MANAGER",
                "shaoxing-keqiao",
                "oil_fume_warning_count",
                "fact_oil_fume_warning",
                "region_code = 'shaoxing-keqiao'"
        );

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header("X-User-Role", "MANAGER")
                        .header("X-User-Region", "shaoxing-keqiao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "请帮我统计2026年1月的油烟预警情况，按照预警类型生成饼图，并做简单分析",
                                "sql", sql
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.rows[0].WARNING_LEVEL").exists())
                .andExpect(jsonPath("$.data.rows[0].METRIC_VALUE").exists());
    }

    @Test
    void shouldPreviewAndExecuteKeqiaoOilFumeClosureRateByStreetRanking() throws Exception {
        String sql = previewSql(
                "查询柯桥区当前各街道油烟预警闭环率排行",
                "MANAGER",
                "shaoxing-keqiao",
                "oil_fume_closure_rate",
                "fact_oil_fume_warning_event",
                "region_code = 'shaoxing-keqiao'"
        );

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header("X-User-Role", "MANAGER")
                        .header("X-User-Region", "shaoxing-keqiao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "查询柯桥区当前各街道油烟预警闭环率排行",
                                "sql", sql
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.rows[0].STREET_NAME").exists())
                .andExpect(jsonPath("$.data.rows[0].METRIC_VALUE").exists());
    }

    @Test
    void shouldPreviewAndExecuteKeqiaoOilFumeStreetRanking() throws Exception {
        String sql = previewSql(
                "查询柯桥区当前各街道油烟预警排行",
                "MANAGER",
                "shaoxing-keqiao",
                "oil_fume_warning_count",
                "fact_oil_fume_warning",
                "region_code = 'shaoxing-keqiao'"
        );

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header("X-User-Role", "MANAGER")
                        .header("X-User-Region", "shaoxing-keqiao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "查询柯桥区当前各街道油烟预警排行",
                                "sql", sql
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.rows[0].STREET_NAME").exists())
                .andExpect(jsonPath("$.data.rows[0].METRIC_VALUE").exists());
    }

    @Test
    void shouldPreviewAndExecuteKeqiaoUnitRepeatWarningRanking() throws Exception {
        String sql = previewSql(
                "查询柯桥区近半年反复预警次数最多的餐饮单位排行",
                "MANAGER",
                "shaoxing-keqiao",
                "oil_fume_repeat_warning_count",
                "fact_oil_fume_warning_event",
                "region_code = 'shaoxing-keqiao'"
        );

        mockMvc.perform(post("/api/v1/data/query/execute")
                        .header("X-User-Role", "MANAGER")
                        .header("X-User-Region", "shaoxing-keqiao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "查询柯桥区近半年反复预警次数最多的餐饮单位排行",
                                "sql", sql
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.rows[0].UNIT_NAME").exists())
                .andExpect(jsonPath("$.data.rows[0].METRIC_VALUE").exists());
    }

    @Test
    void shouldAnswerCompositeOilFumeQuestionInOneResponse() throws Exception {
        mockMvc.perform(post("/api/v1/data/query/answer")
                        .header("X-User-Role", "MANAGER")
                        .header("X-User-Region", "shaoxing-keqiao")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                                "question", "请问柯桥区当前的油烟浓度超标阀值是多少，与以前相比有什么变化，当前还有多少油烟浓度超标的预警未闭环。"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("composite"))
                .andExpect(jsonPath("$.data.answer").value(containsString("2.00 mg/m³")))
                .andExpect(jsonPath("$.data.answer").value(containsString("未闭环油烟超标预警")))
                .andExpect(jsonPath("$.data.answer").value(not(containsString("近7天"))))
                .andExpect(jsonPath("$.data.answer").value(not(containsString("http"))))
                .andExpect(jsonPath("$.data.citations[0].documentTitle").value("饮食业油烟排放标准（试行）"))
                .andExpect(jsonPath("$.data.citations[0].sourceUrl").value(containsString("mee.gov.cn")))
                .andExpect(jsonPath("$.data.dataStatements.length()").value(1))
                .andExpect(jsonPath("$.data.dataStatements[0].queryId").isNotEmpty())
                .andExpect(jsonPath("$.data.queryCards.length()").value(1))
                .andExpect(jsonPath("$.data.queryCards[0].queryId").isNotEmpty())
                .andExpect(jsonPath("$.data.queryCards[0].metricCode").value("oil_fume_unclosed_warning_count"));
    }

    @Test
    void shouldAnswerOilFumeMonitoringTrendWhenQuestionAsksTrendExplicitly() throws Exception {
        mockMvc.perform(post("/api/v1/data/query/answer")
                        .header("X-User-Role", "MANAGER")
                        .header("X-User-Region", "shaoxing-keqiao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "请分析柯桥区近7天油烟平均浓度趋势，并说明当前还有多少油烟浓度超标的预警未闭环。"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("composite"))
                .andExpect(jsonPath("$.data.answer").value(containsString("近7天油烟平均浓度")))
                .andExpect(jsonPath("$.data.queryCards.length()").value(2))
                .andExpect(jsonPath("$.data.queryCards[0].metricCode").value("oil_fume_avg_concentration"))
                .andExpect(jsonPath("$.data.queryCards[1].metricCode").value("oil_fume_unclosed_warning_count"));
    }

    private String previewSql(String question,
                              String role,
                              String region,
                              String expectedMetricCode,
                              String expectedTable,
                              String expectedPermissionRewrite) throws Exception {
        String response = mockMvc.perform(post("/api/v1/data/query/preview")
                        .header("X-User-Role", role)
                        .header("X-User-Region", region)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", question))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queryId").isNotEmpty())
                .andExpect(jsonPath("$.data.metricCode").value(expectedMetricCode))
                .andExpect(jsonPath("$.data.validatedSql").value(containsString(expectedTable)))
                .andExpect(jsonPath("$.data.validatedSql").value(containsString(expectedPermissionRewrite)))
                .andExpect(jsonPath("$.data.dataStatement.caliberVersion").isNotEmpty())
                .andExpect(jsonPath("$.data.dataStatement.dataUpdatedAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        return data.path("validatedSql").asText();
    }
}
