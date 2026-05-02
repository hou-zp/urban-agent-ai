package com.example.urbanagent.query.application;

import com.example.urbanagent.agent.application.dto.MessageCitationView;
import com.example.urbanagent.agent.application.QuestionParsingService;
import com.example.urbanagent.agent.application.dto.AnalysisIntent;
import com.example.urbanagent.agent.application.dto.DataQueryIntent;
import com.example.urbanagent.agent.application.dto.KnowledgeIntent;
import com.example.urbanagent.agent.application.dto.ParsedQuestion;
import com.example.urbanagent.agent.application.dto.UnderstandingSource;
import com.example.urbanagent.agent.application.dto.UrbanScene;
import com.example.urbanagent.audit.application.AuditLogService;
import com.example.urbanagent.common.runtime.RequestRateLimiter;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.example.urbanagent.query.application.dto.DataFragment;
import com.example.urbanagent.query.application.dto.DataStatement;
import com.example.urbanagent.query.application.dto.ExecuteQueryRequest;
import com.example.urbanagent.query.application.dto.PreviewQueryRequest;
import com.example.urbanagent.query.application.dto.QueryAnswerView;
import com.example.urbanagent.query.application.dto.QueryCardView;
import com.example.urbanagent.query.application.dto.QueryExecuteView;
import com.example.urbanagent.query.application.dto.QueryPreviewView;
import com.example.urbanagent.query.domain.MetricDefinition;
import com.example.urbanagent.query.domain.QueryRecord;
import com.example.urbanagent.query.domain.QueryRecordStatus;
import com.example.urbanagent.query.repository.MetricDefinitionRepository;
import com.example.urbanagent.query.repository.QueryRecordRepository;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class QueryApplicationService {

    private static final Logger log = LoggerFactory.getLogger(QueryApplicationService.class);

    private final Nl2SqlService nl2SqlService;
    private final SqlValidationService sqlValidationService;
    private final SqlPermissionService sqlPermissionService;
    private final ReadonlySqlQueryService readonlySqlQueryService;
    private final QueryRecordRepository queryRecordRepository;
    private final MetricDefinitionRepository metricDefinitionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final RequestRateLimiter requestRateLimiter;
    private final AuditLogService auditLogService;
    private final QuestionParsingService questionParsingService;
    private final OrganizationDimensionTranslator organizationDimensionTranslator;

    public QueryApplicationService(Nl2SqlService nl2SqlService,
                                   SqlValidationService sqlValidationService,
                                   SqlPermissionService sqlPermissionService,
                                   ReadonlySqlQueryService readonlySqlQueryService,
                                   QueryRecordRepository queryRecordRepository,
                                   MetricDefinitionRepository metricDefinitionRepository,
                                   JdbcTemplate jdbcTemplate,
                                   RequestRateLimiter requestRateLimiter,
                                   AuditLogService auditLogService,
                                   QuestionParsingService questionParsingService,
                                   OrganizationDimensionTranslator organizationDimensionTranslator) {
        this.nl2SqlService = nl2SqlService;
        this.sqlValidationService = sqlValidationService;
        this.sqlPermissionService = sqlPermissionService;
        this.readonlySqlQueryService = readonlySqlQueryService;
        this.queryRecordRepository = queryRecordRepository;
        this.metricDefinitionRepository = metricDefinitionRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.requestRateLimiter = requestRateLimiter;
        this.auditLogService = auditLogService;
        this.questionParsingService = questionParsingService;
        this.organizationDimensionTranslator = organizationDimensionTranslator;
    }

    @Observed(name = "urban.query.preview")
    @Transactional
    public QueryPreviewView preview(PreviewQueryRequest request) {
        requestRateLimiter.checkQueryPreviewRequest();
        return previewInternal(request, true, parseQuestionForQuery(request.question()));
    }

    @Observed(name = "urban.query.answer")
    @Transactional
    public QueryAnswerView answer(PreviewQueryRequest request) {
        return answer(request, parseQuestionForQuery(request.question()));
    }

    @Transactional
    public QueryAnswerView answer(PreviewQueryRequest request, ParsedQuestion parsedQuestion) {
        requestRateLimiter.checkQueryPreviewRequest();
        String normalizedQuestion = normalize(request.question());
        if (isCompositeOilFumeQuestion(parsedQuestion, normalizedQuestion)) {
            return answerCompositeOilFumeQuestion(parsedQuestion, normalizedQuestion);
        }

        QueryPreviewView preview = previewInternal(request, true, parsedQuestion);
        QueryExecuteView result = executeInternal(new ExecuteQueryRequest(request.question(), preview.validatedSql()), true);
        QueryCardView card = toQueryCard(request.question(), preview, result);
        return new QueryAnswerView(
                "single",
                buildSingleAnswer(card),
                preview.warnings(),
                List.of(card.dataStatement()),
                List.of(card),
                List.of()
        );
    }

    @Observed(name = "urban.query.execute")
    @Transactional
    public QueryExecuteView execute(ExecuteQueryRequest request) {
        requestRateLimiter.checkQueryExecuteRequest();
        return executeInternal(request, true);
    }

    private QueryPreviewView previewInternal(PreviewQueryRequest request, boolean recordQuery, ParsedQuestion parsedQuestion) {
        Instant startedAt = Instant.now();
        log.info("query preview started");
        GeneratedSql generatedSql = nl2SqlService.generate(request.question(), parsedQuestion);
        ValidatedSql validatedSql = sqlValidationService.validate(generatedSql.candidateSql());
        PermissionRewrittenSql rewrittenSql = sqlPermissionService.rewrite(validatedSql);
        StatementMetadata metadata = buildPreviewMetadata(generatedSql, rewrittenSql);
        QueryRecord queryRecord = null;

        if (recordQuery) {
            queryRecord = new QueryRecord(
                    UserContextHolder.get().userId(),
                    request.question(),
                    QueryRecordStatus.PREVIEWED
            );
            queryRecord.markPreview(generatedSql.candidateSql(), rewrittenSql.summary(), generatedSql.summary());
            queryRecord.applyStatementMetadata(
                    metadata.metricCode(),
                    metadata.metricName(),
                    metadata.sourceSummary(),
                    metadata.scopeSummary(),
                    metadata.dataUpdatedAt(),
                    metadata.caliberVersion()
            );
            queryRecordRepository.save(queryRecord);
            auditLogService.recordQueryAccess(queryRecord, Math.max(0L, Instant.now().toEpochMilli() - startedAt.toEpochMilli()));
        }
        log.info("query preview completed");

        DataStatement dataStatement = queryRecord == null
                ? new DataStatement(null, generatedSql.metricCode(), generatedSql.metricName(), metadata.sourceSummary(), metadata.scopeSummary(), metadata.dataUpdatedAt(), rewrittenSql.summary(), metadata.caliberVersion(), "当前阶段为预览结果，尚未执行查询")
                : buildDataStatement(queryRecord, rewrittenSql.summary(), "当前阶段为预览结果，尚未执行查询");

        return new QueryPreviewView(
                queryRecord == null ? null : queryRecord.getId(),
                generatedSql.metricCode(),
                generatedSql.metricName(),
                generatedSql.candidateSql(),
                rewrittenSql.sql(),
                rewrittenSql.summary(),
                generatedSql.summary(),
                generatedSql.warnings(),
                dataStatement
        );
    }

    private ParsedQuestion parseQuestionForQuery(String question) {
        try {
            return questionParsingService.analyze(question);
        } catch (Exception ex) {
            log.debug("query question parsing skipped, fallback to text nl2sql", ex);
            return null;
        }
    }

    private QueryExecuteView executeInternal(ExecuteQueryRequest request, boolean recordQuery) {
        Instant startedAt = Instant.now();
        log.info("query execute started");
        ValidatedSql validatedSql = sqlValidationService.validate(request.sql());
        PermissionRewrittenSql rewrittenSql = sqlPermissionService.rewrite(validatedSql);
        List<Map<String, Object>> rows = translateRows(sanitizeRows(readonlySqlQueryService.execute(rewrittenSql.sql())));
        String resultSummary = rows.isEmpty() ? "未查询到符合条件的数据" : "执行完成，返回 " + rows.size() + " 行";
        QueryRecord queryRecord = null;
        StatementMetadata metadata = buildExecuteMetadata(request.sql(), rewrittenSql, resultSummary);

        if (recordQuery) {
            queryRecord = new QueryRecord(
                    UserContextHolder.get().userId(),
                    request.question(),
                    QueryRecordStatus.EXECUTED
            );
            queryRecord.markExecuted(request.sql(), rewrittenSql.sql(), rewrittenSql.summary(), resultSummary);
            queryRecord.applyStatementMetadata(
                    metadata.metricCode(),
                    metadata.metricName(),
                    metadata.sourceSummary(),
                    metadata.scopeSummary(),
                    metadata.dataUpdatedAt(),
                    metadata.caliberVersion()
            );
            queryRecordRepository.save(queryRecord);
            auditLogService.recordQueryAccess(queryRecord, Math.max(0L, Instant.now().toEpochMilli() - startedAt.toEpochMilli()));
        }
        log.info("query execute completed, rowCount={}", rows.size());

        DataStatement dataStatement = queryRecord == null
                ? new DataStatement(null, metadata.metricCode(), metadata.metricName(), metadata.sourceSummary(), metadata.scopeSummary(), metadata.dataUpdatedAt(), rewrittenSql.summary(), metadata.caliberVersion(), rows.isEmpty() ? "当前范围未命中数据" : "结果已按现有权限过滤")
                : buildDataStatement(queryRecord, rewrittenSql.summary(), rows.isEmpty() ? "当前范围未命中数据" : "结果已按现有权限过滤");

        return new QueryExecuteView(
                queryRecord == null ? null : queryRecord.getId(),
                rewrittenSql.sql(),
                resultSummary,
                rows.size(),
                Instant.now(),
                rows,
                dataStatement
        );
    }

    private QueryAnswerView answerCompositeOilFumeQuestion(ParsedQuestion parsedQuestion, String normalizedQuestion) {
        List<String> warnings = new ArrayList<>();
        List<QueryCardView> cards = new ArrayList<>();
        LocalDate today = LocalDate.now();
        boolean modelAvailable = hasModelUnderstanding(parsedQuestion);
        boolean thresholdIntent = modelAvailable
                ? hasOilFumeThresholdIntent(parsedQuestion)
                : containsAny(normalizedQuestion, "阈值", "阀值", "标准");
        boolean trendIntent = modelAvailable
                ? hasOilFumeMonitoringTrendIntent(parsedQuestion)
                : containsAny(normalizedQuestion, "趋势", "走势", "环比", "同比", "近7天", "最近7天", "近一周", "平均浓度", "最高浓度", "监测浓度");
        boolean unclosedIntent = modelAvailable
                ? hasMetricIntent(parsedQuestion, "oil_fume_unclosed_warning_count")
                : containsAny(normalizedQuestion, "未闭环", "未处理", "待处理", "待办结", "未办结", "未完成");

        QueryCardView recentTrendSeries = null;
        QueryCardView recentAverageCard = null;
        QueryCardView historicalAverageCard = null;
        QueryCardView unclosedCard = null;
        LocalDate unclosedSnapshotDate = today;

        if (trendIntent) {
            warnings.add("未明确变化周期，已按最近7天与更早历史留存监测数据进行比较。");
            recentTrendSeries = runStructuredQuery(
                    "近7天油烟平均浓度变化趋势",
                    "oil_fume_avg_concentration",
                    today.minusDays(6),
                    today,
                    List.of("warning_date"),
                    List.of()
            );
            recentAverageCard = runStructuredQuery(
                    "近7天油烟平均浓度",
                    "oil_fume_avg_concentration",
                    today.minusDays(6),
                    today,
                    List.of(),
                    List.of()
            );
            historicalAverageCard = runStructuredQuery(
                    "更早历史留存油烟平均浓度",
                    "oil_fume_avg_concentration",
                    LocalDate.of(2000, 1, 1),
                    today.minusDays(7),
                    List.of(),
                    List.of("历史对比按当前可用的更早留存数据计算。")
            );
            cards.add(recentTrendSeries);
        }

        if (unclosedIntent) {
            unclosedSnapshotDate = latestMetricDate("oil_fume_unclosed_warning_count", today);
            unclosedCard = runStructuredQuery(
                    "当前未闭环油烟超标预警数量",
                    "oil_fume_unclosed_warning_count",
                    unclosedSnapshotDate,
                    unclosedSnapshotDate,
                    List.of(),
                    List.of()
            );
            cards.add(unclosedCard);
        }

        String answer = buildCompositeOilFumeAnswer(
                thresholdIntent,
                trendIntent,
                unclosedIntent,
                recentTrendSeries,
                recentAverageCard,
                historicalAverageCard,
                unclosedCard,
                unclosedSnapshotDate
        );
        List<QueryCardView> resultCards = cards.stream().filter(Objects::nonNull).toList();
        return new QueryAnswerView(
                "composite",
                answer,
                warnings,
                resultCards.stream().map(QueryCardView::dataStatement).toList(),
                resultCards,
                thresholdIntent ? buildThresholdCitations() : List.of()
        );
    }

    private QueryCardView runStructuredQuery(String question,
                                             String metricCode,
                                             LocalDate start,
                                             LocalDate end,
                                             List<String> dimensions,
                                             List<String> warnings) {
        MetricDefinition metric = metricDefinitionRepository.findByMetricCode(metricCode)
                .filter(MetricDefinition::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("metric not found: " + metricCode));
        String candidateSql = buildMetricSql(metric, start, end, dimensions);
        ValidatedSql validatedSql = sqlValidationService.validate(candidateSql);
        PermissionRewrittenSql rewrittenSql = sqlPermissionService.rewrite(validatedSql);
        List<Map<String, Object>> rows = translateRows(sanitizeRows(readonlySqlQueryService.execute(rewrittenSql.sql())));
        String resultSummary = rows.isEmpty() ? "未查询到符合条件的数据" : "执行完成，返回 " + rows.size() + " 行";

        QueryRecord queryRecord = new QueryRecord(
                UserContextHolder.get().userId(),
                question,
                QueryRecordStatus.EXECUTED
        );
        queryRecord.markExecuted(candidateSql, rewrittenSql.sql(), rewrittenSql.summary(), resultSummary);
        String scopeSummary = "指标：" + metric.getMetricName() + "；时间范围：" + start + " 至 " + end + (dimensions.isEmpty() ? "" : "；维度：" + String.join(", ", dimensions));
        queryRecord.applyStatementMetadata(
                metric.getMetricCode(),
                metric.getMetricName(),
                "数据表：" + metric.getTableName(),
                scopeSummary,
                metric.resolveDataUpdatedAt(),
                metric.resolveCaliberVersion()
        );
        queryRecordRepository.save(queryRecord);

        return new QueryCardView(
                queryRecord.getId(),
                question,
                metric.getMetricCode(),
                metric.getMetricName(),
                scopeSummary,
                resultSummary,
                rewrittenSql.summary(),
                buildDataFragment(queryRecord.getId(), rows, resultSummary),
                buildDataStatement(queryRecord, rewrittenSql.summary(), rows.isEmpty() ? "当前范围未命中数据" : "结果已按现有权限过滤"),
                warnings,
                rows.size(),
                Instant.now(),
                rows
        );
    }

    private LocalDate latestMetricDate(String metricCode, LocalDate fallback) {
        return metricDefinitionRepository.findByMetricCode(metricCode)
                .filter(MetricDefinition::isEnabled)
                .map(metric -> {
                    String sql = "select max(" + metric.getDefaultTimeField() + ") from " + metric.getTableName()
                            + " where " + metric.getDefaultTimeField() + " <= ?";
                    try {
                        LocalDate latestDate = jdbcTemplate.queryForObject(sql, LocalDate.class, fallback);
                        return latestDate == null ? fallback : latestDate;
                    } catch (RuntimeException ex) {
                        log.warn("failed to resolve latest metric date for {}, fallback to {}", metricCode, fallback);
                        return fallback;
                    }
                })
                .orElse(fallback);
    }

    private String buildMetricSql(MetricDefinition metric,
                                  LocalDate start,
                                  LocalDate end,
                                  List<String> dimensions) {
        StringBuilder sql = new StringBuilder("select ");
        if (!dimensions.isEmpty()) {
            sql.append(String.join(", ", dimensions)).append(", ");
        }
        sql.append(metric.getAggregationExpr()).append(" as metric_value")
                .append(" from ").append(metric.getTableName())
                .append(" where ").append(metric.getDefaultTimeField())
                .append(" between '").append(start).append("' and '").append(end).append("'");
        if (!dimensions.isEmpty()) {
            sql.append(" group by ").append(String.join(", ", dimensions));
            if (dimensions.size() == 1 && metric.getDefaultTimeField().equalsIgnoreCase(dimensions.get(0))) {
                sql.append(" order by ").append(dimensions.get(0)).append(" asc");
            } else {
                sql.append(" order by metric_value desc");
            }
            sql.append(" limit 50");
        } else {
            sql.append(" limit 1");
        }
        return sql.toString();
    }

    private QueryCardView toQueryCard(String question, QueryPreviewView preview, QueryExecuteView result) {
        QueryRecord queryRecord = result.queryId() == null ? null : queryRecordRepository.findById(result.queryId()).orElse(null);
        DataStatement dataStatement = queryRecord == null
                ? new DataStatement(result.queryId(), preview.metricCode(), preview.metricName(), "数据来源待确认", preview.summary(), result.executedAt(), preview.permissionRewrite(), "manual-sql", "结果已按现有权限过滤")
                : buildDataStatement(queryRecord, preview.permissionRewrite(), result.rows().isEmpty() ? "当前范围未命中数据" : "结果已按现有权限过滤");
        return new QueryCardView(
                result.queryId(),
                question,
                preview.metricCode(),
                preview.metricName(),
                preview.summary(),
                result.summary(),
                preview.permissionRewrite(),
                buildDataFragment(result.queryId(), result.rows(), result.summary()),
                dataStatement,
                preview.warnings(),
                result.rowCount(),
                result.executedAt(),
                result.rows()
        );
    }

    private StatementMetadata buildPreviewMetadata(GeneratedSql generatedSql, PermissionRewrittenSql rewrittenSql) {
        MetricDefinition metric = generatedSql.metricCode() == null ? null : metricDefinitionRepository.findByMetricCode(generatedSql.metricCode())
                .filter(MetricDefinition::isEnabled)
                .orElse(null);
        String metricName = generatedSql.metricName() == null && metric != null ? metric.getMetricName() : generatedSql.metricName();
        String sourceSummary = metric == null ? "候选 SQL 来源：" + firstFromTable(rewrittenSql.sql()) : "数据表：" + metric.getTableName();
        String caliberVersion = metric == null ? "preview-sql" : metric.resolveCaliberVersion();
        Instant dataUpdatedAt = metric == null ? Instant.now() : metric.resolveDataUpdatedAt();
        return new StatementMetadata(
                generatedSql.metricCode(),
                metricName,
                sourceSummary,
                generatedSql.summary(),
                dataUpdatedAt,
                caliberVersion
        );
    }

    private StatementMetadata buildExecuteMetadata(String candidateSql,
                                                   PermissionRewrittenSql rewrittenSql,
                                                   String resultSummary) {
        String tableName = firstFromTable(candidateSql);
        MetricDefinition metric = tableName == null ? null : metricDefinitionRepository.findAll().stream()
                .filter(MetricDefinition::isEnabled)
                .filter(item -> item.getTableName().equalsIgnoreCase(tableName))
                .findFirst()
                .orElse(null);
        String sourceSummary = tableName == null ? "数据来源待确认" : "数据表：" + tableName;
        return new StatementMetadata(
                metric == null ? null : metric.getMetricCode(),
                metric == null ? "只读查询结果" : metric.getMetricName(),
                sourceSummary,
                resultSummary + "；权限改写：" + rewrittenSql.summary(),
                metric == null ? Instant.now() : metric.resolveDataUpdatedAt(),
                metric == null ? "manual-sql" : metric.resolveCaliberVersion()
        );
    }

    private DataFragment buildDataFragment(String queryId, List<Map<String, Object>> rows, String summary) {
        List<String> fields = rows.isEmpty()
                ? List.of()
                : rows.get(0).keySet().stream().map(String::valueOf).toList();
        return new DataFragment(queryId, fields, rows.size(), summary);
    }

    private DataStatement buildDataStatement(QueryRecord queryRecord, String permissionRewrite, String limitation) {
        return new DataStatement(
                queryRecord.getId(),
                queryRecord.getMetricCode(),
                queryRecord.getMetricName(),
                queryRecord.getSourceSummary(),
                queryRecord.getScopeSummary(),
                queryRecord.getDataUpdatedAt(),
                permissionRewrite,
                queryRecord.getCaliberVersion(),
                limitation
        );
    }

    private String firstFromTable(String sql) {
        if (sql == null || sql.isBlank()) {
            return null;
        }
        String normalized = sql.replaceAll("\\s+", " ").trim();
        String[] segments = normalized.split("(?i) from ", 2);
        if (segments.length < 2) {
            return null;
        }
        return Arrays.stream(segments[1].split(" "))
                .findFirst()
                .map(token -> token.replaceAll("[,;]", ""))
                .orElse(null);
    }

    private String buildSingleAnswer(QueryCardView card) {
        Double primaryValue = firstMetricValue(card);
        if (primaryValue == null) {
            return "我已经完成这次智能问数，但当前条件下没有查到符合条件的数据。建议补充时间范围、街道或预警类型后再试。";
        }

        String metricName = card.metricName() == null ? "统计结果" : card.metricName();
        String rankingSummary = buildRankingSummary(card, metricName);
        if (!rankingSummary.isBlank()) {
            return rankingSummary;
        }
        String valueText = formatMetricValue(primaryValue, metricName);
        if (metricName.contains("未闭环") && metricName.contains("油烟")) {
            return "柯桥区当前未闭环油烟浓度超标预警为 " + valueText + "。建议优先核查持续未处置点位，并结合街道分布安排闭环。";
        }
        if (metricName.contains("闭环率")) {
            return "柯桥区当前" + metricName + "为 " + valueText + "。可结合街道分布和反复预警单位名单，优先补齐薄弱点位。";
        }
        if (metricName.contains("反复预警次数")) {
            return "已统计出本次关注范围内的" + metricName + "，当前结果为 " + valueText + "。可进一步查看具体单位排名，锁定反复预警对象。";
        }
        if (metricName.contains("油烟") && metricName.contains("浓度")) {
            return "柯桥区当前" + metricName + "为 " + valueText + "。建议结合历史变化和未闭环预警数量一起研判。";
        }
        return metricName + "为 " + valueText + "。已按您的问题整理为可直接查看的结果。";
    }

    private String buildRankingSummary(QueryCardView card, String metricName) {
        if (card == null || card.rows().size() <= 1) {
            return "";
        }
        Map<String, Object> firstRow = card.rows().get(0);
        if (firstRow.containsKey("STREET_NAME") || firstRow.containsKey("street_name")) {
            return buildTopItemsSummary(card.rows(), metricName, "STREET_NAME", "当前街道排行中");
        }
        if (firstRow.containsKey("UNIT_NAME") || firstRow.containsKey("unit_name")) {
            return buildTopItemsSummary(card.rows(), metricName, "UNIT_NAME", "当前单位排行中");
        }
        return "";
    }

    private String buildTopItemsSummary(List<Map<String, Object>> rows,
                                        String metricName,
                                        String key,
                                        String prefix) {
        List<String> items = rows.stream()
                .limit(3)
                .map(row -> {
                    Object name = row.getOrDefault(key, row.get(key.toLowerCase(Locale.ROOT)));
                    Double value = readMetricValueOrNull(row);
                    if (name == null || value == null) {
                        return null;
                    }
                    return name + " " + formatMetricValue(value, metricName);
                })
                .filter(Objects::nonNull)
                .toList();
        if (items.isEmpty()) {
            return "";
        }
        return prefix + "，" + String.join("，", items) + "。";
    }

    private String buildCompositeOilFumeAnswer(boolean thresholdIntent,
                                               boolean trendIntent,
                                               boolean unclosedIntent,
                                               QueryCardView recentTrendSeries,
                                               QueryCardView recentAverageCard,
                                               QueryCardView historicalAverageCard,
                                               QueryCardView unclosedCard,
                                               LocalDate unclosedSnapshotDate) {
        List<String> sections = new ArrayList<>();

        if (thresholdIntent) {
            sections.add(buildThresholdAnswer());
        }

        if (trendIntent) {
            Double recentAverage = firstMetricValue(recentAverageCard);
            Double historicalAverage = firstMetricValue(historicalAverageCard);
            String trendText;
            if (recentAverage == null) {
                trendText = "趋势变化：当前近7天没有取到可用监测数据，暂时无法判断浓度变化。";
            } else if (historicalAverage == null) {
                trendText = "趋势变化：近7天油烟平均浓度为 " + formatMetricValue(recentAverage, "油烟平均浓度") + "，但更早历史留存数据不足，暂时无法形成对比。";
            } else {
                double delta = recentAverage - historicalAverage;
                String direction = delta > 0 ? "上升" : delta < 0 ? "下降" : "基本持平";
                trendText = "趋势变化：因问题未指定对比周期，已按最近7天与历史留存均值进行比较，近7天油烟平均浓度为 "
                        + formatMetricValue(recentAverage, "油烟平均浓度")
                        + "，历史对比值为 " + formatMetricValue(historicalAverage, "油烟平均浓度")
                        + "，整体" + direction;
                if (delta != 0D) {
                    trendText += " " + formatMetricValue(Math.abs(delta), "油烟平均浓度");
                }
                trendText += "。";
                String peakText = buildPeakText(recentTrendSeries);
                if (!peakText.isBlank()) {
                    trendText += peakText;
                }
            }
            sections.add(trendText);
        }

        if (unclosedIntent) {
            Double unclosedValue = firstMetricValue(unclosedCard);
            if (unclosedValue == null) {
                sections.add("未闭环预警：当前没有取到未闭环油烟超标预警的有效数据。");
            } else {
                sections.add("未闭环预警：截至" + unclosedSnapshotDate + "，柯桥区未闭环油烟超标预警为 " + formatMetricValue(unclosedValue, "未闭环油烟预警数量") + "。");
            }
        }

        return String.join("\n\n", sections);
    }

    private String buildThresholdAnswer() {
        List<OilFumeThresholdConfig> configs = loadOilFumeThresholdConfigs();
        if (configs.isEmpty()) {
            return "当前阈值口径：测试库中暂未配置油烟超标阈值，请先补充阈值配置表数据。";
        }

        OilFumeThresholdConfig current = configs.get(0);
        StringBuilder builder = new StringBuilder("当前阈值口径：根据")
                .append(current.standardCode()).append("《").append(current.standardName()).append("》")
                .append("，饮食业单位油烟最高允许排放浓度为 ")
                .append(formatMetricValue(current.maxAllowedConcentration(), "油烟平均浓度"))
                .append("；油烟净化设施最低去除效率为小型 ")
                .append(current.removalEfficiencySmall()).append("%、中型 ")
                .append(current.removalEfficiencyMedium()).append("%、大型 ")
                .append(current.removalEfficiencyLarge()).append("%。");

        if (configs.size() > 1) {
            OilFumeThresholdConfig previous = configs.get(1);
            boolean unchanged = Double.compare(current.maxAllowedConcentration(), previous.maxAllowedConcentration()) == 0
                    && current.removalEfficiencySmall() == previous.removalEfficiencySmall()
                    && current.removalEfficiencyMedium() == previous.removalEfficiencyMedium()
                    && current.removalEfficiencyLarge() == previous.removalEfficiencyLarge();
            if (unchanged) {
                builder.append(" 与上一版").append(previous.standardCode()).append("相比，当前阈值口径未发生变化。");
            } else {
                builder.append(" 与上一版").append(previous.standardCode()).append("相比，当前阈值口径已调整。");
            }
        }

        return builder.toString();
    }

    private List<MessageCitationView> buildThresholdCitations() {
        List<OilFumeThresholdConfig> configs = loadOilFumeThresholdConfigs();
        if (configs.isEmpty()) {
            return List.of();
        }
        OilFumeThresholdConfig current = configs.get(0);
        if (current.sourceUrl() == null || current.sourceUrl().isBlank()) {
            return List.of();
        }
        return List.of(new MessageCitationView(
                "standard:" + current.standardCode(),
                current.standardName(),
                null,
                "STANDARD",
                "中华人民共和国生态环境部",
                current.standardCode(),
                current.sourceUrl(),
                current.standardCode() + "《" + current.standardName() + "》规定了饮食业单位油烟最高允许排放浓度和油烟净化设施最低去除效率。",
                "标准原文",
                current.effectiveFrom(),
                null
        ));
    }

    private List<OilFumeThresholdConfig> loadOilFumeThresholdConfigs() {
        return jdbcTemplate.query(
                """
                select standard_code, standard_name, max_allowed_concentration,
                       removal_efficiency_small, removal_efficiency_medium, removal_efficiency_large,
                       source_url, effective_from
                from oil_fume_threshold_config
                where region_code = ? and enabled = true
                order by effective_from desc
                """,
                (rs, rowNum) -> new OilFumeThresholdConfig(
                        rs.getString("standard_code"),
                        rs.getString("standard_name"),
                        rs.getDouble("max_allowed_concentration"),
                        rs.getInt("removal_efficiency_small"),
                        rs.getInt("removal_efficiency_medium"),
                        rs.getInt("removal_efficiency_large"),
                        rs.getString("source_url"),
                        rs.getObject("effective_from", LocalDate.class)
                ),
                "shaoxing-keqiao"
        );
    }

    private String buildPeakText(QueryCardView recentTrendSeries) {
        if (recentTrendSeries == null || recentTrendSeries.rows().isEmpty()) {
            return "";
        }
        Map<String, Object> peakRow = recentTrendSeries.rows().stream()
                .filter(Objects::nonNull)
                .max((left, right) -> Double.compare(readMetricValue(left), readMetricValue(right)))
                .orElse(null);
        if (peakRow == null) {
            return "";
        }
        Object dateValue = peakRow.getOrDefault("WARNING_DATE", peakRow.get("warning_date"));
        double metricValue = readMetricValue(peakRow);
        if (dateValue == null || !Double.isFinite(metricValue)) {
            return "";
        }
        return "其中 " + dateValue + " 的平均浓度最高，为 " + formatMetricValue(metricValue, "油烟平均浓度") + "。";
    }

    private Double firstMetricValue(QueryCardView card) {
        if (card == null || card.rows().isEmpty()) {
            return null;
        }
        return readMetricValueOrNull(card.rows().get(0));
    }

    private Double readMetricValueOrNull(Map<String, Object> row) {
        double value = readMetricValue(row);
        return Double.isFinite(value) ? value : null;
    }

    private double readMetricValue(Map<String, Object> row) {
        if (row == null) {
            return Double.NaN;
        }
        Object value = row.getOrDefault("METRIC_VALUE", row.get("metric_value"));
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    private String formatMetricValue(double value, String metricName) {
        if (metricName.contains("浓度")) {
            return String.format(Locale.ROOT, "%.2f mg/m³", value);
        }
        if (metricName.contains("率")) {
            return String.format(Locale.ROOT, "%.2f%%", value);
        }
        if (metricName.contains("次数")) {
            return Math.rint(value) == value
                    ? String.format(Locale.ROOT, "%.0f 次", value)
                    : String.format(Locale.ROOT, "%.2f 次", value);
        }
        if (Math.rint(value) == value) {
            return String.format(Locale.ROOT, "%.0f 起", value);
        }
        return String.format(Locale.ROOT, "%.2f 起", value);
    }

    private boolean isCompositeOilFumeQuestion(ParsedQuestion parsedQuestion, String normalizedQuestion) {
        if (hasModelUnderstanding(parsedQuestion)) {
            if (!hasOilFumeScene(parsedQuestion)) {
                return false;
            }
            int intentCount = 0;
            if (hasOilFumeThresholdIntent(parsedQuestion)) {
                intentCount++;
            }
            if (hasOilFumeMonitoringTrendIntent(parsedQuestion)) {
                intentCount++;
            }
            if (hasMetricIntent(parsedQuestion, "oil_fume_unclosed_warning_count")) {
                intentCount++;
            }
            return intentCount >= 2;
        }

        if (!containsAny(normalizedQuestion, "油烟", "餐饮", "浓度", "油烟扰民")) {
            return false;
        }
        int fallbackIntentCount = 0;
        if (containsAny(normalizedQuestion, "阈值", "阀值", "标准")) {
            fallbackIntentCount++;
        }
        if (containsAny(normalizedQuestion, "趋势", "走势", "环比", "同比", "近7天", "最近7天", "近一周", "平均浓度", "最高浓度", "监测浓度")) {
            fallbackIntentCount++;
        }
        if (containsAny(normalizedQuestion, "未闭环", "未处理", "待处理", "待办结", "未办结", "未完成")) {
            fallbackIntentCount++;
        }
        return fallbackIntentCount >= 2;
    }

    private boolean hasModelUnderstanding(ParsedQuestion parsedQuestion) {
        return parsedQuestion != null
                && parsedQuestion.understanding() != null
                && parsedQuestion.understanding().source() != UnderstandingSource.RULE_FALLBACK;
    }

    private boolean hasOilFumeScene(ParsedQuestion parsedQuestion) {
        return parsedQuestion.scenes().contains(UrbanScene.CATERING_OIL_FUME)
                || parsedQuestion.dataIntents().stream()
                .map(DataQueryIntent::metricCode)
                .anyMatch(metricCode -> metricCode.startsWith("oil_fume_"));
    }

    private boolean hasOilFumeThresholdIntent(ParsedQuestion parsedQuestion) {
        return hasOilFumeScene(parsedQuestion)
                && parsedQuestion.knowledgeIntents().stream()
                .anyMatch(intent -> intent == KnowledgeIntent.OIL_FUME_THRESHOLD
                        || intent == KnowledgeIntent.OIL_FUME_THRESHOLD_CHANGE);
    }

    private boolean hasOilFumeMonitoringTrendIntent(ParsedQuestion parsedQuestion) {
        return parsedQuestion.dataIntents().stream()
                .filter(dataIntent -> dataIntent.analysisIntent() == AnalysisIntent.TREND
                        || dataIntent.analysisIntent() == AnalysisIntent.COMPARISON)
                .map(DataQueryIntent::metricCode)
                .anyMatch(metricCode -> "oil_fume_avg_concentration".equals(metricCode)
                        || "oil_fume_max_concentration".equals(metricCode));
    }

    private boolean hasMetricIntent(ParsedQuestion parsedQuestion, String metricCode) {
        return parsedQuestion.dataIntents().stream()
                .map(DataQueryIntent::metricCode)
                .anyMatch(metricCode::equals);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private List<Map<String, Object>> sanitizeRows(List<Map<String, Object>> rows) {
        return rows.stream()
                .filter(this::containsMeaningfulValue)
                .toList();
    }

    private List<Map<String, Object>> translateRows(List<Map<String, Object>> rows) {
        return organizationDimensionTranslator.translate(rows);
    }

    private boolean containsMeaningfulValue(Map<String, Object> row) {
        return row.values().stream().anyMatch(this::isMeaningfulValue);
    }

    private boolean isMeaningfulValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String stringValue) {
            return !stringValue.isBlank();
        }
        return true;
    }

    private record StatementMetadata(
            String metricCode,
            String metricName,
            String sourceSummary,
            String scopeSummary,
            Instant dataUpdatedAt,
            String caliberVersion
    ) {
    }

    private record OilFumeThresholdConfig(
            String standardCode,
            String standardName,
            double maxAllowedConcentration,
            int removalEfficiencySmall,
            int removalEfficiencyMedium,
            int removalEfficiencyLarge,
            String sourceUrl,
            LocalDate effectiveFrom
    ) {
    }
}
