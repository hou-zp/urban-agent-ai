package com.example.urbanagent.query.application;

import com.example.urbanagent.agent.application.dto.AnalysisIntent;
import com.example.urbanagent.agent.application.dto.DataQueryIntent;
import com.example.urbanagent.agent.application.dto.ParsedQuestion;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.query.domain.MetricDefinition;
import com.example.urbanagent.query.repository.MetricDefinitionRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class Nl2SqlService {

    private static final Pattern CHINESE_MONTH_PATTERN = Pattern.compile("(20\\d{2})年(1[0-2]|0?[1-9])月");

    private final MetricDefinitionRepository metricDefinitionRepository;

    public Nl2SqlService(MetricDefinitionRepository metricDefinitionRepository) {
        this.metricDefinitionRepository = metricDefinitionRepository;
    }

    public GeneratedSql generate(String question) {
        return generate(question, null);
    }

    public GeneratedSql generate(String question, ParsedQuestion parsedQuestion) {
        String normalizedQuestion = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        validateQuestionScope(normalizedQuestion);
        List<String> warnings = new ArrayList<>();
        MetricDefinition metric = selectMetric(normalizedQuestion, parsedQuestion, warnings);
        List<String> dimensions = resolveDimensions(metric, normalizedQuestion, parsedQuestion, warnings);
        DateRange dateRange = resolveDateRange(metric.getDefaultTimeField(), normalizedQuestion);
        boolean ranking = isAnalysisIntent(parsedQuestion, AnalysisIntent.RANKING)
                || normalizedQuestion.contains("排行")
                || normalizedQuestion.contains("排名");
        String regionCode = resolveRegionCode(metric, normalizedQuestion);
        int limit = dimensions.isEmpty() ? 1 : (ranking ? 20 : 50);

        if (dateRange.defaulted()) {
            warnings.add("问题未明确时间范围，已默认最近7天。");
        }

        StringBuilder sql = new StringBuilder("select ");
        if (!dimensions.isEmpty()) {
            sql.append(String.join(", ", dimensions)).append(", ");
        }
        sql.append(metric.getAggregationExpr()).append(" as metric_value")
                .append(" from ").append(metric.getTableName())
                .append(" where ").append(metric.getDefaultTimeField())
                .append(" between '").append(dateRange.start()).append("' and '").append(dateRange.end()).append("'");
        if (regionCode != null) {
            sql.append(" and region_code = '").append(regionCode).append("'");
        }
        if (!dimensions.isEmpty()) {
            sql.append(" group by ").append(String.join(", ", dimensions));
        }
        if (ranking || !dimensions.isEmpty()) {
            sql.append(" order by metric_value desc");
        }
        sql.append(" limit ").append(limit);

        return new GeneratedSql(
                metric.getMetricCode(),
                metric.getMetricName(),
                sql.toString(),
                buildSummary(metric, dimensions, dateRange),
                warnings
        );
    }

    private void validateQuestionScope(String question) {
        if (!isOilFumeQuestion(question)) {
            return;
        }

        boolean thresholdIntent = containsAny(question, "阈值", "阀值", "标准");
        boolean trendIntent = containsAny(question, "变化", "趋势", "环比", "同比", "以前", "历史", "对比");
        boolean unclosedIntent = containsAny(question, "未闭环", "未处理", "待处理", "待办结", "未办结", "未完成");

        if (thresholdIntent) {
            String message = "当前暂未配置“油烟超标阈值”指标。现阶段可直接查询的指标包括：油烟超标预警数量、未闭环油烟预警数量、油烟平均浓度、油烟最高浓度。";
            if (trendIntent || unclosedIntent) {
                message += "您这次同时问了阈值、历史变化和未闭环数量，建议拆开提问。";
            }
            throw new BusinessException(ErrorCode.METRIC_NOT_FOUND, message);
        }

        if (trendIntent && unclosedIntent) {
            throw new BusinessException(
                    ErrorCode.METRIC_NOT_FOUND,
                    "当前问题同时包含历史变化和未闭环数量，建议拆开提问，例如“近半年油烟平均浓度有什么变化”“当前还有多少油烟超标预警未闭环”。"
            );
        }
    }

    private MetricDefinition selectMetric(String question) {
        return metricDefinitionRepository.findByEnabledTrueOrderByMetricCodeAsc()
                .stream()
                .map(metric -> new ScoredMetric(metric, scoreMetric(metric, question)))
                .filter(candidate -> candidate.score() > 0)
                .max((left, right) -> Integer.compare(left.score(), right.score()))
                .map(ScoredMetric::metric)
                .or(() -> metricDefinitionRepository.findByEnabledTrueOrderByMetricCodeAsc()
                        .stream()
                        .filter(metric -> matchesMetric(metric, question))
                        .findFirst())
                .orElseThrow(() -> new BusinessException(ErrorCode.METRIC_NOT_FOUND, "无法识别可用指标"));
    }

    private MetricDefinition selectMetric(String question, ParsedQuestion parsedQuestion, List<String> warnings) {
        Optional<String> modelMetricCode = parsedQuestion == null
                ? Optional.empty()
                : parsedQuestion.dataIntent()
                .map(DataQueryIntent::metricCode)
                .filter(metricCode -> !metricCode.isBlank());
        if (modelMetricCode.isPresent()) {
            Optional<MetricDefinition> metric = metricDefinitionRepository.findByMetricCode(modelMetricCode.get())
                    .filter(MetricDefinition::isEnabled);
            if (metric.isPresent()) {
                return metric.get();
            }
            warnings.add("模型识别的指标 " + modelMetricCode.get() + " 当前未启用，已改用规则识别。");
        }
        return selectMetric(question);
    }

    private int scoreMetric(MetricDefinition metric, String question) {
        String metricCode = metric.getMetricCode().toLowerCase(Locale.ROOT);
        String metricName = metric.getMetricName().toLowerCase(Locale.ROOT);
        int score = 0;
        if (question.contains(metricCode) || question.contains(metricName)) {
            score += 120;
        }
        score += scoreKnownMetric(metricCode, question);
        score += scoreByBusinessText(metric, question);
        return score;
    }

    private int scoreKnownMetric(String metricCode, String question) {
        return switch (metricCode) {
            case "complaint_count" -> containsAny(question, "投诉", "工单", "热线") ? 80 : 0;
            case "inspection_problem_count" -> containsAny(question, "巡查", "网格", "问题") ? 70 : 0;
            case "overdue_case_count" -> containsAny(question, "超期", "案件", "逾期") ? 85 : 0;
            case "oil_fume_warning_count" -> scoreOilFumeWarningCount(question);
            case "oil_fume_unclosed_warning_count" -> scoreOilFumeUnclosedCount(question);
            case "oil_fume_avg_concentration" -> scoreOilFumeAverage(question);
            case "oil_fume_max_concentration" -> scoreOilFumeMaximum(question);
            case "oil_fume_closure_rate" -> scoreOilFumeClosureRate(question);
            case "oil_fume_repeat_warning_count" -> scoreOilFumeRepeatWarningCount(question);
            default -> 0;
        };
    }

    private int scoreOilFumeWarningCount(String question) {
        if (!isOilFumeQuestion(question)) {
            return 0;
        }
        if (containsAny(question, "街道排行", "街道排名", "镇街排行", "镇街排名")) {
            return 125;
        }
        if (containsAny(question, "未闭环", "未处理", "待处理", "待办结", "未办结", "未完成")) {
            return 70;
        }
        if (containsAny(question, "预警", "超标", "数量", "多少", "统计", "总量")) {
            return 100;
        }
        return 60;
    }

    private int scoreOilFumeUnclosedCount(String question) {
        if (!isOilFumeQuestion(question)) {
            return 0;
        }
        return containsAny(question, "未闭环", "未处理", "待处理", "待办结", "未办结", "未完成") ? 130 : 0;
    }

    private int scoreOilFumeAverage(String question) {
        if (!isOilFumeQuestion(question)) {
            return 0;
        }
        if (containsAny(question, "平均", "均值")) {
            return 115;
        }
        if (containsAny(question, "浓度", "变化", "趋势", "环比", "同比")) {
            return 80;
        }
        return 0;
    }

    private int scoreOilFumeMaximum(String question) {
        if (!isOilFumeQuestion(question)) {
            return 0;
        }
        return containsAny(question, "最高", "最大", "峰值") ? 120 : 0;
    }

    private int scoreOilFumeClosureRate(String question) {
        if (!isOilFumeQuestion(question)) {
            return 0;
        }
        int score = containsAny(question, "闭环率", "办结率", "处置率") ? 140 : 0;
        if (score > 0 && containsAny(question, "排行", "排名", "街道", "镇街")) {
            score += 15;
        }
        return score;
    }

    private int scoreOilFumeRepeatWarningCount(String question) {
        if (!isOilFumeQuestion(question)) {
            return 0;
        }
        int score = 0;
        if (containsAny(question, "反复预警", "重复预警", "多次预警")) {
            score += 140;
        }
        if (containsAny(question, "单位", "商户", "餐饮店", "门店")) {
            score += 25;
        }
        if (containsAny(question, "次数", "几次", "多少次")) {
            score += 20;
        }
        return score;
    }

    private boolean isOilFumeQuestion(String question) {
        return containsAny(question, "油烟", "餐饮", "浓度", "油烟扰民");
    }

    private int scoreByBusinessText(MetricDefinition metric, String question) {
        String businessText = String.join(" ",
                metric.getMetricName(),
                metric.getDescription(),
                metric.getCommonDimensions(),
                metric.getTableName()
        ).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : questionTokens(question)) {
            if (businessText.contains(token)) {
                score += token.length() >= 3 ? 8 : 4;
            }
        }
        return score;
    }

    private List<String> questionTokens(String question) {
        if (question.isBlank()) {
            return List.of();
        }
        if (question.length() <= 4) {
            return List.of(question);
        }
        return java.util.stream.IntStream.range(0, question.length() - 1)
                .mapToObj(index -> question.substring(index, index + 2))
                .distinct()
                .toList();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesMetric(MetricDefinition metric, String question) {
        if (question.contains("投诉")) {
            return "complaint_count".equals(metric.getMetricCode());
        }
        if (question.contains("巡查")) {
            return "inspection_problem_count".equals(metric.getMetricCode());
        }
        if (question.contains("超期") || question.contains("案件")) {
            return "overdue_case_count".equals(metric.getMetricCode());
        }
        return question.contains(metric.getMetricCode().toLowerCase(Locale.ROOT))
                || question.contains(metric.getMetricName().toLowerCase(Locale.ROOT));
    }

    private List<String> resolveDimensions(MetricDefinition metric,
                                           String question,
                                           ParsedQuestion parsedQuestion,
                                           List<String> warnings) {
        LinkedHashSet<String> dimensions = new LinkedHashSet<>();
        DataQueryIntent dataIntent = parsedQuestion == null ? null : parsedQuestion.dataIntent().orElse(null);
        List<String> textDimensions = resolveTextDimensions(metric, question);
        if (dataIntent != null && modelMetricMatches(metric, dataIntent)) {
            for (String dimension : dataIntent.dimensions()) {
                String physicalDimension = resolvePhysicalDimension(metric, dimension);
                if (physicalDimension != null || metric.getDefaultTimeField().equalsIgnoreCase(dimension)) {
                    dimensions.add(physicalDimension == null ? dimension : physicalDimension);
                } else {
                    warnings.add("模型识别的维度 " + dimension + " 当前指标不支持，已忽略。");
                }
            }
            if (dimensions.isEmpty()) {
                applyDefaultDimension(metric, dataIntent.analysisIntent(), dimensions);
            }
            if (!textDimensions.isEmpty() && hasExplicitDimensionCue(question) && !List.copyOf(dimensions).equals(textDimensions)) {
                warnings.add("问题中明确指定了维度，已按文本维度纠偏模型识别结果。");
                return textDimensions;
            }
        }
        if (!dimensions.isEmpty()) {
            return List.copyOf(dimensions);
        }

        return textDimensions;
    }

    private List<String> resolveTextDimensions(MetricDefinition metric, String question) {
        LinkedHashSet<String> dimensions = new LinkedHashSet<>();

        boolean ranking = question.contains("排行") || question.contains("排名");
        boolean distribution = containsAny(question, "分布", "构成", "集中", "主要集中", "各街道", "各镇街");
        if (question.contains("街道") || question.contains("镇街")) {
            addDimensionIfSupported(metric, dimensions, "street_name");
        }
        if (containsAny(question, "区域", "区县", "行政区") && supportsDimension(metric, "region_code")) {
            dimensions.add("region_code");
        }
        if (question.contains("网格") && metric.getTableName().contains("inspection")) {
            dimensions.add("grid_name");
        }
        if (question.contains("状态") && metric.getTableName().contains("case")) {
            dimensions.add("case_status");
        }
        if (containsAny(question, "单位", "商户", "餐饮店", "门店")
                && "oil_fume_repeat_warning_count".equals(metric.getMetricCode())) {
            dimensions.add("unit_name");
        }
        if (containsAny(question, "等级", "级别", "类型", "类别", "分类") && metric.getTableName().contains("oil_fume")) {
            dimensions.add("warning_level");
        }
        if (containsAny(question, "趋势", "变化", "历史", "对比", "环比", "同比")
                && metric.getTableName().contains("oil_fume")) {
            dimensions.add(metric.getDefaultTimeField());
        }
        if ((ranking || distribution) && dimensions.isEmpty()) {
            addDimensionIfSupported(metric, dimensions, "street_name");
        }
        return List.copyOf(dimensions);
    }

    private void applyDefaultDimension(MetricDefinition metric,
                                       AnalysisIntent analysisIntent,
                                       LinkedHashSet<String> dimensions) {
        if (analysisIntent == null) {
            return;
        }
        switch (analysisIntent) {
            case DISTRIBUTION, RANKING -> {
                addDimensionIfSupported(metric, dimensions, "street_name");
            }
            case TREND -> dimensions.add(metric.getDefaultTimeField());
            case TOTAL, COMPARISON, DETAIL, ANALYSIS -> {
            }
        }
    }

    private boolean isAnalysisIntent(ParsedQuestion parsedQuestion, AnalysisIntent expected) {
        return parsedQuestion != null && parsedQuestion.analysisIntent()
                .filter(intent -> intent == expected)
                .isPresent();
    }

    private boolean modelMetricMatches(MetricDefinition metric, DataQueryIntent dataIntent) {
        return dataIntent.metricCode().isBlank() || metric.getMetricCode().equals(dataIntent.metricCode());
    }

    private boolean hasExplicitDimensionCue(String question) {
        return containsAny(question,
                "分布", "构成", "集中", "主要集中",
                "街道", "镇街", "区域", "区县", "行政区", "网格", "状态", "单位", "商户", "餐饮店", "门店", "等级", "级别", "类型", "类别", "分类"
        );
    }

    private void addDimensionIfSupported(MetricDefinition metric,
                                         LinkedHashSet<String> dimensions,
                                         String semanticDimension) {
        String physicalDimension = resolvePhysicalDimension(metric, semanticDimension);
        if (physicalDimension != null) {
            dimensions.add(physicalDimension);
        }
    }

    private String resolvePhysicalDimension(MetricDefinition metric, String semanticDimension) {
        if (semanticDimension == null || semanticDimension.isBlank()) {
            return null;
        }
        if ("street_name".equalsIgnoreCase(semanticDimension) && supportsDimension(metric, "street_code")) {
            return "street_code";
        }
        if (supportsDimension(metric, semanticDimension)) {
            return semanticDimension;
        }
        return null;
    }

    private boolean supportsDimension(MetricDefinition metric, String dimension) {
        if (metric.getCommonDimensions() == null || metric.getCommonDimensions().isBlank()) {
            return false;
        }
        return java.util.Arrays.stream(metric.getCommonDimensions().split(","))
                .map(String::trim)
                .anyMatch(item -> item.equalsIgnoreCase(dimension));
    }

    private String resolveRegionCode(MetricDefinition metric, String question) {
        if (question.contains("柯桥区")
                && ("shaoxing-keqiao".equalsIgnoreCase(metric.getRegionCode())
                || "绍兴市柯桥区".equals(metric.getApplicableRegion()))) {
            return "shaoxing-keqiao";
        }
        return null;
    }

    private DateRange resolveDateRange(String timeField, String question) {
        LocalDate today = LocalDate.now();
        Matcher monthMatcher = CHINESE_MONTH_PATTERN.matcher(question);
        if (monthMatcher.find()) {
            YearMonth yearMonth = YearMonth.of(
                    Integer.parseInt(monthMatcher.group(1)),
                    Integer.parseInt(monthMatcher.group(2))
            );
            return new DateRange(yearMonth.atDay(1), yearMonth.atEndOfMonth(), false);
        }
        if (question.contains("今天") || question.contains("今日") || question.contains("当前") || question.contains("目前") || question.contains("现在")) {
            return new DateRange(today, today, false);
        }
        if (question.contains("本周")) {
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return new DateRange(monday, today, false);
        }
        if (question.contains("本月")) {
            LocalDate firstDay = today.withDayOfMonth(1);
            return new DateRange(firstDay, today, false);
        }
        if (question.contains("近半年")) {
            return new DateRange(today.minusMonths(6).plusDays(1), today, false);
        }
        if (question.contains("近一年")) {
            return new DateRange(today.minusYears(1).plusDays(1), today, false);
        }
        return new DateRange(today.minusDays(6), today, true);
    }

    private String buildSummary(MetricDefinition metric, List<String> dimensions, DateRange dateRange) {
        StringBuilder builder = new StringBuilder()
                .append("指标：").append(metric.getMetricName())
                .append("；时间范围：").append(dateRange.start()).append(" 至 ").append(dateRange.end());
        if (!dimensions.isEmpty()) {
            builder.append("；维度：").append(String.join(", ", dimensions));
        }
        return builder.toString();
    }

    private record DateRange(LocalDate start, LocalDate end, boolean defaulted) {
    }

    private record ScoredMetric(MetricDefinition metric, int score) {
    }
}
