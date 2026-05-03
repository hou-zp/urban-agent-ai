package com.example.urbanagent.query.application;

import com.example.urbanagent.agent.application.dto.AnalysisIntent;
import com.example.urbanagent.agent.application.dto.DataQueryIntent;
import com.example.urbanagent.agent.application.dto.ParsedQuestion;
import com.example.urbanagent.agent.application.dto.QuestionType;
import com.example.urbanagent.agent.application.dto.QuestionUnderstanding;
import com.example.urbanagent.agent.application.dto.UnderstandingSource;
import com.example.urbanagent.agent.application.dto.UrbanScene;
import com.example.urbanagent.query.domain.MetricDefinition;
import com.example.urbanagent.query.repository.MetricDefinitionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Nl2SqlServiceTest {

    private final MetricDefinition complaintMetric = new MetricDefinition(
            "complaint_count",
            "投诉数量",
            "投诉工单数量",
            "count(*)",
            "complaint_time",
            "street_code,region_code,case_status",
            "fact_complaint_order",
            "primary-jdbc",
            "shaoxing-keqiao",
            "CITY-COMPLAINT-2026.01",
            "verified",
            "绍兴市柯桥区",
            Instant.parse("2026-05-01T00:00:00Z"),
            true
    );

    @Test
    void shouldGenerateGroupByStreetFromDistributionIntent() {
        Nl2SqlService service = newService();

        GeneratedSql generatedSql = service.generate(
                "本周柯桥区投诉数量分布",
                parsedQuestion(AnalysisIntent.DISTRIBUTION, List.of("street_name"))
        );

        assertThat(generatedSql.candidateSql()).contains("group by street_code");
        assertThat(generatedSql.candidateSql()).contains("order by metric_value desc");
        assertThat(generatedSql.candidateSql()).contains("limit 50");
    }

    @Test
    void shouldGenerateStreetRankingFromRankingIntent() {
        Nl2SqlService service = newService();

        GeneratedSql generatedSql = service.generate(
                "本周柯桥区投诉数量排行",
                parsedQuestion(AnalysisIntent.RANKING, List.of("street_name"))
        );

        assertThat(generatedSql.candidateSql()).contains("group by street_code");
        assertThat(generatedSql.candidateSql()).contains("order by metric_value desc");
        assertThat(generatedSql.candidateSql()).contains("limit 20");
    }

    @Test
    void shouldIgnoreUnsupportedModelDimension() {
        Nl2SqlService service = newService();

        GeneratedSql generatedSql = service.generate(
                "本周柯桥区投诉数量分布",
                parsedQuestion(AnalysisIntent.DISTRIBUTION, List.of("unit_name"))
        );

        assertThat(generatedSql.candidateSql()).doesNotContain("unit_name");
        assertThat(generatedSql.candidateSql()).contains("group by street_code");
        assertThat(generatedSql.warnings()).anyMatch(warning -> warning.contains("unit_name"));
    }

    @Test
    void shouldCorrectGenericDistributionToStreetWhenModelReturnsRegionCode() {
        Nl2SqlService service = newService();

        GeneratedSql generatedSql = service.generate(
                "本周柯桥区投诉数量分布",
                parsedQuestion(AnalysisIntent.DISTRIBUTION, List.of("region_code"))
        );

        assertThat(generatedSql.candidateSql()).contains("group by street_code");
        assertThat(generatedSql.candidateSql()).doesNotContain("group by region_code");
        assertThat(generatedSql.warnings()).anyMatch(warning -> warning.contains("文本维度纠偏"));
    }

    @Test
    void shouldKeepRegionCodeWhenQuestionExplicitlyAsksRegionDistribution() {
        Nl2SqlService service = newService();

        GeneratedSql generatedSql = service.generate(
                "本周投诉数量按区域分布",
                parsedQuestion(AnalysisIntent.DISTRIBUTION, List.of("region_code"))
        );

        assertThat(generatedSql.candidateSql()).contains("group by region_code");
        assertThat(generatedSql.candidateSql()).doesNotContain("group by street_code");
    }

    @Test
    void shouldKeepLegacyTextRulesWhenParsedQuestionMissing() {
        Nl2SqlService service = newService();

        GeneratedSql generatedSql = service.generate("查询本周各街道投诉数量排行");

        assertThat(generatedSql.candidateSql()).contains("group by street_code");
        assertThat(generatedSql.candidateSql()).contains("order by metric_value desc");
    }

    private Nl2SqlService newService() {
        MetricDefinitionRepository repository = mock(MetricDefinitionRepository.class);
        when(repository.findByEnabledTrueOrderByMetricCodeAsc()).thenReturn(List.of(complaintMetric));
        when(repository.findByMetricCode("complaint_count")).thenReturn(Optional.of(complaintMetric));
        return new Nl2SqlService(repository);
    }

    private ParsedQuestion parsedQuestion(AnalysisIntent analysisIntent, List<String> dimensions) {
        QuestionUnderstanding understanding = new QuestionUnderstanding(
                List.of(QuestionType.METRIC_QUERY),
                QuestionType.METRIC_QUERY,
                List.of(UrbanScene.STREET_ORDER),
                new DataQueryIntent("complaint_count", analysisIntent, dimensions, "本周", "shaoxing-keqiao"),
                false,
                false,
                0.92D,
                UnderstandingSource.MODEL
        );
        return new ParsedQuestion(
                "本周柯桥区投诉数量分布",
                List.of(),
                List.of(UrbanScene.STREET_ORDER),
                List.of(),
                0.92D,
                understanding
        );
    }
}
