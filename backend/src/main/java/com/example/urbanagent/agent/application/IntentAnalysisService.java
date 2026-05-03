package com.example.urbanagent.agent.application;

import com.example.urbanagent.ai.application.StructuredOutputGateway;
import com.example.urbanagent.agent.application.dto.AnalysisIntent;
import com.example.urbanagent.agent.application.dto.DataQueryIntent;
import com.example.urbanagent.agent.application.dto.ExtractedSlot;
import com.example.urbanagent.agent.application.dto.IntentType;
import com.example.urbanagent.agent.application.dto.KnowledgeIntent;
import com.example.urbanagent.agent.application.dto.ParsedIntent;
import com.example.urbanagent.agent.application.dto.ParsedQuestion;
import com.example.urbanagent.agent.application.dto.QuestionType;
import com.example.urbanagent.agent.application.dto.QuestionUnderstanding;
import com.example.urbanagent.agent.application.dto.SlotType;
import com.example.urbanagent.agent.application.dto.UnderstandingSource;
import com.example.urbanagent.agent.application.dto.UrbanScene;
import com.example.urbanagent.agent.domain.QuestionParseRecord;
import com.example.urbanagent.agent.repository.QuestionParseRecordRepository;
import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IntentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(IntentAnalysisService.class);
    private static final TypeReference<List<ParsedIntent>> PARSED_INTENT_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<UrbanScene>> SCENE_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ExtractedSlot>> SLOT_TYPE = new TypeReference<>() {
    };
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("(20\\d{2})年(1[0-2]|0?[1-9])月");
    private static final Pattern ISO_YEAR_MONTH_PATTERN = Pattern.compile("(20\\d{2})-(1[0-2]|0?[1-9])");
    private static final Pattern STREET_PATTERN = Pattern.compile("([\\p{IsHan}A-Za-z0-9]{2,12}(街道|镇))");
    private static final int MODEL_UNDERSTANDING_MAX_RETRIES = 1;
    private static final double MODEL_PRIMARY_CONFIDENCE = 0.75D;
    private static final double MODEL_MERGE_CONFIDENCE = 0.55D;
    private static final String MODEL_UNDERSTANDING_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "required": ["questionTypes", "primaryQuestionType", "scenes", "dataIntent", "needCitation", "needSuggestion", "confidence"],
              "properties": {
                "questionTypes": {
                  "type": "array",
                  "items": { "$ref": "#/$defs/questionType" },
                  "uniqueItems": true
                },
                "primaryQuestionType": {
                  "$ref": "#/$defs/questionType"
                },
                "scenes": {
                  "type": "array",
                  "items": {
                    "type": "string",
                    "enum": ["CITY_APPEARANCE", "ENV_SANITATION", "CATERING_GOVERNANCE", "CATERING_OIL_FUME", "WATER_GREEN_SPACE", "URBAN_SPACE", "IDLE_LAND", "STREET_ORDER", "EMERGENCY_RESPONSE"]
                  }
                },
                "dataIntent": {
                  "anyOf": [
                    { "type": "null" },
                    { "$ref": "#/$defs/dataQueryIntent" }
                  ]
                },
                "dataIntents": {
                  "type": "array",
                  "items": { "$ref": "#/$defs/dataQueryIntent" }
                },
                "knowledgeIntents": {
                  "type": "array",
                  "items": {
                    "type": "string",
                    "enum": ["OIL_FUME_THRESHOLD", "OIL_FUME_THRESHOLD_CHANGE", "GENERAL_POLICY"]
                  },
                  "uniqueItems": true
                },
                "needCitation": { "type": "boolean" },
                "needSuggestion": { "type": "boolean" },
                "confidence": { "type": "number", "minimum": 0, "maximum": 1 }
              },
              "$defs": {
                "questionType": {
                  "type": "string",
                  "enum": ["METRIC_QUERY", "BUSINESS_DATA_QUERY", "LEGAL_ADVICE", "POLICY_INTERPRETATION", "BUSINESS_CONSULTATION"]
                },
                "dataQueryIntent": {
                  "type": "object",
                  "required": ["metricCode", "analysisIntent", "dimensions", "timeExpression", "regionCode"],
                  "properties": {
                    "metricCode": { "type": "string" },
                    "analysisIntent": {
                      "type": "string",
                      "enum": ["TOTAL", "DISTRIBUTION", "RANKING", "TREND", "COMPARISON", "DETAIL", "ANALYSIS"]
                    },
                    "dimensions": {
                      "type": "array",
                      "items": {
                        "type": "string",
                        "enum": ["street_name", "grid_name", "case_status", "warning_level", "unit_name", "region_code"]
                      },
                      "uniqueItems": true
                    },
                    "timeExpression": { "type": "string" },
                    "regionCode": { "type": "string" }
                  },
                  "additionalProperties": false
                }
              },
              "additionalProperties": false
            }
            """;

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final QuestionParseRecordRepository questionParseRecordRepository;
    private final StructuredOutputGateway structuredOutputGateway;

    @Autowired
    public IntentAnalysisService(ObjectMapper objectMapper,
                                  QuestionParseRecordRepository questionParseRecordRepository,
                                  StructuredOutputGateway structuredOutputGateway) {
        this(Clock.systemDefaultZone(), objectMapper, questionParseRecordRepository, structuredOutputGateway);
    }

    IntentAnalysisService(Clock clock,
                           ObjectMapper objectMapper,
                           QuestionParseRecordRepository questionParseRecordRepository,
                           StructuredOutputGateway structuredOutputGateway) {
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.questionParseRecordRepository = questionParseRecordRepository;
        this.structuredOutputGateway = structuredOutputGateway;
    }

    public ParsedQuestion analyze(String question) {
        String originalQuestion = question == null ? "" : question.trim();
        if (originalQuestion.isBlank()) {
            return new ParsedQuestion(originalQuestion, List.of(), List.of(), List.of(), 0.2D);
        }

        String normalized = normalize(originalQuestion);
        List<ParsedIntent> intents = extractIntents(normalized);
        List<UrbanScene> scenes = extractScenes(normalized);
        List<ExtractedSlot> slots = extractSlots(originalQuestion, normalized, intents, scenes);
        ParsedQuestion ruleParsedQuestion = new ParsedQuestion(
                originalQuestion,
                List.copyOf(intents),
                List.copyOf(scenes),
                List.copyOf(slots),
                calculateConfidence(intents, slots)
        );
        return analyzeWithModelFirst(originalQuestion, ruleParsedQuestion);
    }

    @Transactional
    public ParsedQuestion analyzeAndSave(String runId, String question) {
        ParsedQuestion parsedQuestion = analyze(question);
        if (questionParseRecordRepository == null || runId == null || runId.isBlank()) {
            return parsedQuestion;
        }
        questionParseRecordRepository.save(new QuestionParseRecord(
                runId,
                parsedQuestion.originalQuestion(),
                parsedQuestion.primaryIntent().map(Enum::name).orElse(null),
                parsedQuestion.confidence(),
                parsedQuestion.requiresCitation(),
                parsedQuestion.hasMandatoryDataIntent(),
                writeJson(parsedQuestion.intents()),
                writeJson(parsedQuestion.scenes()),
                writeJson(parsedQuestion.slots())
        ));
        return parsedQuestion;
    }

    @Transactional(readOnly = true)
    public Optional<ParsedQuestion> findByRunId(String runId) {
        if (questionParseRecordRepository == null || runId == null || runId.isBlank()) {
            return Optional.empty();
        }
        return questionParseRecordRepository.findById(runId).map(this::toParsedQuestion);
    }

    public ParsedQuestion loadOrAnalyze(String runId, String question) {
        return findByRunId(runId).orElseGet(() -> analyze(question));
    }

    private List<ParsedIntent> extractIntents(String normalized) {
        Map<IntentType, ParsedIntent> intents = new LinkedHashMap<>();
        boolean aggregateMetricQuestion = containsAny(normalized,
                "数量", "多少", "几家", "几处", "几宗", "趋势", "排行", "排名", "同比", "环比", "汇总", "占比");
        boolean detailDataCue = containsAny(normalized,
                "明细", "名单", "台账", "记录", "点位", "位置", "地址", "状态", "哪些", "哪几家", "哪几处");
        boolean operationalObject = containsAny(normalized,
                "投诉", "巡查", "案件", "商户", "地块", "点位", "油烟", "垃圾");
        boolean detailDataQuestion = detailDataCue && operationalObject;

        if (aggregateMetricQuestion || operationalObject && containsAny(normalized, "本周", "本月", "最近7天", "最近")) {
            intents.put(IntentType.METRIC_QUERY, new ParsedIntent(
                    IntentType.METRIC_QUERY,
                    true,
                    "命中数量、趋势、排名或周期统计类表达",
                    0.95D
            ));
        }
        if (detailDataQuestion) {
            intents.put(IntentType.BUSINESS_DATA_QUERY, new ParsedIntent(
                    IntentType.BUSINESS_DATA_QUERY,
                    true,
                    "命中点位、商户、案件、状态或明细类表达",
                    0.92D
            ));
        }
        if (containsAny(normalized, "法规", "法律", "条例", "法条", "处罚", "执法", "罚款", "处罚依据")) {
            intents.put(IntentType.LEGAL_ADVICE, new ParsedIntent(
                    IntentType.LEGAL_ADVICE,
                    false,
                    "命中法规、处罚或执法依据类表达",
                    0.91D
            ));
        }
        if (containsAny(normalized, "政策", "解读", "通知", "意见", "方案", "工作要求", "口径")) {
            intents.put(IntentType.POLICY_INTERPRETATION, new ParsedIntent(
                    IntentType.POLICY_INTERPRETATION,
                    false,
                    "命中政策、口径或解读类表达",
                    0.87D
            ));
        }
        if (containsAny(normalized, "流程", "程序", "归口", "材料", "派单", "怎么办", "如何处理", "处置建议", "处置流程", "备案")) {
            intents.put(IntentType.BUSINESS_CONSULTATION, new ParsedIntent(
                    IntentType.BUSINESS_CONSULTATION,
                    false,
                    "命中流程、归口、材料或处置建议类表达",
                    0.84D
            ));
        }
        return List.copyOf(intents.values());
    }

    private List<UrbanScene> extractScenes(String normalized) {
        LinkedHashSet<UrbanScene> scenes = new LinkedHashSet<>();
        if (containsAny(normalized, "市容", "立面", "广告", "乱堆放", "围挡")) {
            scenes.add(UrbanScene.CITY_APPEARANCE);
        }
        if (containsAny(normalized, "垃圾", "满溢", "保洁", "环卫")) {
            scenes.add(UrbanScene.ENV_SANITATION);
        }
        if (containsAny(normalized, "餐饮", "商户")) {
            scenes.add(UrbanScene.CATERING_GOVERNANCE);
        }
        if (containsAny(normalized, "油烟", "餐饮油烟")) {
            scenes.add(UrbanScene.CATERING_OIL_FUME);
        }
        if (containsAny(normalized, "水绿", "绿化", "河道", "公园")) {
            scenes.add(UrbanScene.WATER_GREEN_SPACE);
        }
        if (containsAny(normalized, "空间", "地块", "停车", "围挡")) {
            scenes.add(UrbanScene.URBAN_SPACE);
        }
        if (containsAny(normalized, "空闲地块", "闲置地块")) {
            scenes.add(UrbanScene.IDLE_LAND);
        }
        if (containsAny(normalized, "占道", "共享单车", "摊贩", "围挡备案")) {
            scenes.add(UrbanScene.STREET_ORDER);
        }
        if (containsAny(normalized, "应急", "突发", "险情", "抢险")) {
            scenes.add(UrbanScene.EMERGENCY_RESPONSE);
        }
        return List.copyOf(scenes);
    }

    private List<ExtractedSlot> extractSlots(String originalQuestion,
                                             String normalized,
                                             List<ParsedIntent> intents,
                                             List<UrbanScene> scenes) {
        List<ExtractedSlot> slots = new ArrayList<>();
        extractTimeSlots(originalQuestion, normalized, slots);
        extractRegionSlots(originalQuestion, normalized, intents, scenes, slots);
        extractMetricSlots(normalized, slots);
        extractObjectSlots(normalized, slots);
        extractStatusSlots(normalized, slots);
        extractOutputFormatSlots(normalized, slots);
        return deduplicateSlots(slots);
    }

    private void extractTimeSlots(String originalQuestion, String normalized, List<ExtractedSlot> slots) {
        LocalDate today = LocalDate.now(clock);
        if (normalized.contains("本周")) {
            LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate end = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            slots.add(new ExtractedSlot(SlotType.TIME, "本周", start.format(DATE_FORMATTER) + "~" + end.format(DATE_FORMATTER), true, 0.96D));
        }
        if (normalized.contains("本月")) {
            YearMonth currentMonth = YearMonth.from(today);
            slots.add(new ExtractedSlot(SlotType.TIME, "本月", currentMonth.toString(), true, 0.95D));
        }
        if (normalized.contains("最近7天")) {
            LocalDate start = today.minusDays(6);
            slots.add(new ExtractedSlot(SlotType.TIME, "最近7天", start.format(DATE_FORMATTER) + "~" + today.format(DATE_FORMATTER), true, 0.95D));
        }

        Matcher yearMonthMatcher = YEAR_MONTH_PATTERN.matcher(originalQuestion);
        while (yearMonthMatcher.find()) {
            String normalizedValue = yearMonthMatcher.group(1) + "-" + padMonth(yearMonthMatcher.group(2));
            slots.add(new ExtractedSlot(SlotType.TIME, yearMonthMatcher.group(), normalizedValue, true, 0.93D));
        }

        Matcher isoYearMonthMatcher = ISO_YEAR_MONTH_PATTERN.matcher(originalQuestion);
        while (isoYearMonthMatcher.find()) {
            String normalizedValue = isoYearMonthMatcher.group(1) + "-" + padMonth(isoYearMonthMatcher.group(2));
            slots.add(new ExtractedSlot(SlotType.TIME, isoYearMonthMatcher.group(), normalizedValue, true, 0.92D));
        }
    }

    private void extractRegionSlots(String originalQuestion,
                                    String normalized,
                                    List<ParsedIntent> intents,
                                    List<UrbanScene> scenes,
                                    List<ExtractedSlot> slots) {
        Map<String, String> regionAliases = Map.of(
                "柯桥区", "shaoxing-keqiao",
                "柯桥", "shaoxing-keqiao",
                "越城区", "shaoxing-yuecheng",
                "越城", "shaoxing-yuecheng",
                "全市", "city",
                "市本级", "city"
        );
        boolean explicitRegion = false;
        for (Map.Entry<String, String> entry : regionAliases.entrySet()) {
            if (originalQuestion.contains(entry.getKey()) || normalized.contains(entry.getValue())) {
                slots.add(new ExtractedSlot(SlotType.REGION, entry.getKey(), entry.getValue(), true, 0.94D));
                explicitRegion = true;
            }
        }

        Matcher streetMatcher = STREET_PATTERN.matcher(originalQuestion);
        while (streetMatcher.find()) {
            slots.add(new ExtractedSlot(SlotType.REGION, streetMatcher.group(1), streetMatcher.group(1), true, 0.88D));
            explicitRegion = true;
        }

        boolean operationalQuestion = intents.stream().anyMatch(intent -> intent.mandatory())
                || intents.stream().anyMatch(intent -> intent.intentType() == IntentType.BUSINESS_CONSULTATION)
                || !scenes.isEmpty();
        if (!explicitRegion && operationalQuestion) {
            UserContext userContext = UserContextHolder.get();
            slots.add(new ExtractedSlot(SlotType.REGION, "当前用户区域", userContext.region(), false, 0.81D));
        }
    }

    private void extractMetricSlots(String normalized, List<ExtractedSlot> slots) {
        if (normalized.contains("投诉")) {
            slots.add(new ExtractedSlot(SlotType.METRIC, "投诉", "complaint_count", true, 0.92D));
        }
        if (normalized.contains("巡查")) {
            slots.add(new ExtractedSlot(SlotType.METRIC, "巡查", "inspection_count", true, 0.90D));
        }
        if (normalized.contains("案件")) {
            slots.add(new ExtractedSlot(SlotType.METRIC, "案件", "case_count", true, 0.90D));
        }
        if (normalized.contains("油烟")) {
            slots.add(new ExtractedSlot(SlotType.METRIC, "油烟", "oil_fume_value", true, 0.89D));
        }
        if (normalized.contains("垃圾")) {
            slots.add(new ExtractedSlot(SlotType.METRIC, "垃圾", "garbage_issue_count", true, 0.88D));
        }
    }

    private void extractObjectSlots(String normalized, List<ExtractedSlot> slots) {
        if (normalized.contains("商户")) {
            slots.add(new ExtractedSlot(SlotType.OBJECT, "商户", "merchant", true, 0.90D));
        }
        if (normalized.contains("地块")) {
            slots.add(new ExtractedSlot(SlotType.OBJECT, "地块", "land_plot", true, 0.90D));
        }
        if (normalized.contains("点位")) {
            slots.add(new ExtractedSlot(SlotType.OBJECT, "点位", "point", true, 0.90D));
        }
        if (normalized.contains("案件")) {
            slots.add(new ExtractedSlot(SlotType.OBJECT, "案件", "case", true, 0.88D));
        }
        if (normalized.contains("工单")) {
            slots.add(new ExtractedSlot(SlotType.OBJECT, "工单", "work_order", true, 0.88D));
        }
    }

    private void extractStatusSlots(String normalized, List<ExtractedSlot> slots) {
        if (normalized.contains("异常")) {
            slots.add(new ExtractedSlot(SlotType.STATUS, "异常", "abnormal", false, 0.85D));
        }
        if (normalized.contains("超标")) {
            slots.add(new ExtractedSlot(SlotType.STATUS, "超标", "over_limit", false, 0.88D));
        }
        if (normalized.contains("待处理")) {
            slots.add(new ExtractedSlot(SlotType.STATUS, "待处理", "pending", false, 0.84D));
        }
        if (normalized.contains("已处理")) {
            slots.add(new ExtractedSlot(SlotType.STATUS, "已处理", "resolved", false, 0.84D));
        }
        if (normalized.contains("状态")) {
            slots.add(new ExtractedSlot(SlotType.STATUS, "状态", "status", false, 0.80D));
        }
    }

    private void extractOutputFormatSlots(String normalized, List<ExtractedSlot> slots) {
        if (containsAny(normalized, "排行", "排名")) {
            slots.add(new ExtractedSlot(SlotType.OUTPUT_FORMAT, "排行", "ranking", false, 0.93D));
        }
        if (containsAny(normalized, "分布", "构成", "集中", "主要集中", "各街道", "各镇街")) {
            slots.add(new ExtractedSlot(SlotType.OUTPUT_FORMAT, "分布", "distribution", false, 0.91D));
        }
        if (normalized.contains("趋势")) {
            slots.add(new ExtractedSlot(SlotType.OUTPUT_FORMAT, "趋势", "trend", false, 0.92D));
        }
        if (containsAny(normalized, "同比", "环比", "对比")) {
            slots.add(new ExtractedSlot(SlotType.OUTPUT_FORMAT, "对比", "comparison", false, 0.92D));
        }
        if (containsAny(normalized, "明细", "名单", "台账")) {
            slots.add(new ExtractedSlot(SlotType.OUTPUT_FORMAT, "明细", "detail", false, 0.90D));
        }
        if (containsAny(normalized, "分析", "报告", "研判", "周报", "月报")) {
            slots.add(new ExtractedSlot(SlotType.OUTPUT_FORMAT, "分析", "analysis", false, 0.88D));
        }
    }

    private List<ExtractedSlot> deduplicateSlots(List<ExtractedSlot> slots) {
        Map<String, ExtractedSlot> deduplicated = new LinkedHashMap<>();
        for (ExtractedSlot slot : slots) {
            String key = slot.slotType() + ":" + slot.normalizedValue();
            deduplicated.putIfAbsent(key, slot);
        }
        return List.copyOf(deduplicated.values());
    }

    private ParsedQuestion toParsedQuestion(QuestionParseRecord record) {
        try {
            List<ParsedIntent> intents = objectMapper.readValue(record.getIntentsJson(), PARSED_INTENT_TYPE);
            List<UrbanScene> scenes = objectMapper.readValue(record.getScenesJson(), SCENE_TYPE);
            List<ExtractedSlot> slots = objectMapper.readValue(record.getSlotsJson(), SLOT_TYPE);
            return new ParsedQuestion(record.getOriginalQuestion(), intents, scenes, slots, record.getOverallConfidence());
        } catch (JsonProcessingException ex) {
            log.warn("Failed to deserialize parsed question record, fallback to realtime analysis. runId={}", record.getRunId(), ex);
            return analyze(record.getOriginalQuestion());
        }
    }

    private double calculateConfidence(List<ParsedIntent> intents, List<ExtractedSlot> slots) {
        double intentConfidence = intents.stream()
                .mapToDouble(ParsedIntent::confidence)
                .average()
                .orElse(0.45D);
        double slotBonus = Math.min(slots.size(), 4) * 0.03D;
        return Math.min(0.99D, intentConfidence + slotBonus);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize parsed question", ex);
        }
    }

    private String normalize(String content) {
        return content == null ? "" : content.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String content, String... candidates) {
        for (String candidate : candidates) {
            if (content.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String padMonth(String month) {
        return month.length() == 1 ? "0" + month : month;
    }

    private ParsedQuestion analyzeWithModelFirst(String originalQuestion, ParsedQuestion ruleParsedQuestion) {
        if (structuredOutputGateway == null) {
            return ruleParsedQuestion;
        }
        try {
            StructuredOutputGateway.StructuredOutputResult result = structuredOutputGateway.generate(
                    new StructuredOutputGateway.StructuredOutputRequest(
                            List.of(),
                            buildModelUnderstandingPrompt(originalQuestion),
                            MODEL_UNDERSTANDING_SCHEMA,
                            MODEL_UNDERSTANDING_MAX_RETRIES
                    )
            );
            if (!result.valid()) {
                log.debug("model question understanding skipped due to invalid structured output: {}", result.validationError());
                return ruleParsedQuestion;
            }
            QuestionUnderstanding modelUnderstanding = objectMapper.readValue(result.content(), QuestionUnderstanding.class);
            return applyUnderstandingPolicy(originalQuestion, ruleParsedQuestion, modelUnderstanding);
        } catch (Exception ex) {
            log.debug("model question understanding fallback to rule result", ex);
            return ruleParsedQuestion;
        }
    }

    private String buildModelUnderstandingPrompt(String originalQuestion) {
        return """
                你是城管业务问题的结构化意图识别器。请把用户自然语言归一为固定 JSON，不要生成 SQL，不要回答业务问题。

                识别要求：
                1. questionTypes 可以多选：METRIC_QUERY 表示聚合问数，BUSINESS_DATA_QUERY 表示明细/台账查询，LEGAL_ADVICE 表示法规依据，POLICY_INTERPRETATION 表示政策口径，BUSINESS_CONSULTATION 表示处置建议/流程咨询。
                2. primaryQuestionType 表示最主要的执行入口。纯问数通常是 METRIC_QUERY；法规+问数+建议的混合问题仍以 METRIC_QUERY 为主。
                3. dataIntent 只描述主问数语义。dataIntents 描述全部问数语义；复合问题里有多个指标时必须全部列出。metricCode 只能输出已知指标编码，无法判断时为空字符串；不要输出 SQL。
                4. knowledgeIntents 描述不直接落 SQL 的政策/标准主题：油烟排放阈值输出 OIL_FUME_THRESHOLD，阈值版本变化输出 OIL_FUME_THRESHOLD_CHANGE，其他政策口径输出 GENERAL_POLICY。
                5. analysisIntent 只能取 TOTAL、DISTRIBUTION、RANKING、TREND、COMPARISON、DETAIL、ANALYSIS。
                6. dimensions 只能取 street_name、grid_name、case_status、warning_level、unit_name、region_code。用户说"分布、集中在哪些地方、各街道情况"时，优先识别为 DISTRIBUTION + street_name。
                7. "排行、排名、最多、最少"识别为 RANKING；明确询问监测值趋势、走势、环比、同比时识别为 TREND 或 COMPARISON；"明细、名单、台账、哪些"识别为 DETAIL。
                8. 柯桥区归一为 shaoxing-keqiao，全市归一为 city。
                9. 投诉数量归一为 complaint_count，巡查问题数量归一为 inspection_problem_count，超期案件数量归一为 overdue_case_count，油烟预警数量归一为 oil_fume_warning_count，未闭环油烟预警数量归一为 oil_fume_unclosed_warning_count，油烟平均浓度归一为 oil_fume_avg_concentration，油烟最高浓度归一为 oil_fume_max_concentration。
                10. 需要法规/政策/业务依据时 needCitation=true；需要处置建议时 needSuggestion=true。
                11. 用户问"油烟超标阈值是多少、阈值与以前相比有什么变化"时，这是标准/口径比较，不是监测浓度趋势；knowledgeIntents 应包含 OIL_FUME_THRESHOLD 和 OIL_FUME_THRESHOLD_CHANGE；needCitation=true；不要把 dataIntent 误设为 oil_fume_avg_concentration + TREND。
                12. 只输出 JSON，不输出解释。

                原始问题：
                %s
                """.formatted(originalQuestion);
    }

    private ParsedQuestion applyUnderstandingPolicy(String originalQuestion,
                                                    ParsedQuestion ruleParsedQuestion,
                                                    QuestionUnderstanding modelUnderstanding) {
        if (modelUnderstanding == null
                || modelUnderstanding.confidence() < MODEL_MERGE_CONFIDENCE
                || !hasActionableUnderstanding(modelUnderstanding)) {
            return ruleParsedQuestion;
        }
        ParsedQuestion modelParsedQuestion = toParsedQuestion(originalQuestion, modelUnderstanding);
        if (modelUnderstanding.confidence() >= MODEL_PRIMARY_CONFIDENCE) {
            return applyRuleGuardrails(modelParsedQuestion, ruleParsedQuestion, UnderstandingSource.MODEL);
        }
        return applyRuleGuardrails(mergeParsedQuestions(modelParsedQuestion, ruleParsedQuestion), ruleParsedQuestion, UnderstandingSource.MODEL_WITH_RULE_GUARDRAIL);
    }

    private boolean hasActionableUnderstanding(QuestionUnderstanding understanding) {
        if (!safeList(understanding.questionTypes()).isEmpty()) {
            return true;
        }
        DataQueryIntent dataIntent = understanding.dataIntent();
        return dataIntent != null
                && (!dataIntent.metricCode().isBlank()
                || !dataIntent.dimensions().isEmpty()
                || dataIntent.analysisIntent() != AnalysisIntent.TOTAL);
    }

    private ParsedQuestion toParsedQuestion(String originalQuestion, QuestionUnderstanding understanding) {
        List<ParsedIntent> intents = safeList(understanding.questionTypes()).stream()
                .map(QuestionType::toIntentType)
                .distinct()
                .map(intentType -> new ParsedIntent(
                        intentType,
                        intentType == IntentType.METRIC_QUERY || intentType == IntentType.BUSINESS_DATA_QUERY,
                        "LLM结构化识别",
                        Math.max(0.55D, understanding.confidence())
                ))
                .toList();
        List<ExtractedSlot> slots = slotsFromUnderstanding(understanding);
        QuestionUnderstanding normalizedUnderstanding = new QuestionUnderstanding(
                safeList(understanding.questionTypes()),
                understanding.primaryQuestionType(),
                safeList(understanding.scenes()),
                understanding.dataIntent(),
                normalizeDataIntents(understanding),
                normalizeKnowledgeIntents(understanding),
                understanding.needCitation(),
                understanding.needSuggestion(),
                understanding.confidence(),
                UnderstandingSource.MODEL
        );
        return new ParsedQuestion(
                originalQuestion,
                intents,
                safeList(understanding.scenes()),
                slots,
                understanding.confidence(),
                normalizedUnderstanding
        );
    }

    private List<ExtractedSlot> slotsFromUnderstanding(QuestionUnderstanding understanding) {
        List<ExtractedSlot> slots = new ArrayList<>();
        DataQueryIntent dataIntent = understanding.dataIntent();
        if (dataIntent == null) {
            return slots;
        }
        if (!dataIntent.timeExpression().isBlank()) {
            slots.add(new ExtractedSlot(SlotType.TIME, dataIntent.timeExpression(), dataIntent.timeExpression(), true, understanding.confidence()));
        }
        if (!dataIntent.regionCode().isBlank()) {
            slots.add(new ExtractedSlot(SlotType.REGION, dataIntent.regionCode(), dataIntent.regionCode(), true, understanding.confidence()));
        }
        if (!dataIntent.metricCode().isBlank()) {
            slots.add(new ExtractedSlot(SlotType.METRIC, dataIntent.metricCode(), dataIntent.metricCode(), true, understanding.confidence()));
        }
        String outputFormat = outputFormat(dataIntent.analysisIntent());
        if (!outputFormat.isBlank()) {
            slots.add(new ExtractedSlot(SlotType.OUTPUT_FORMAT, outputFormat, outputFormat, false, understanding.confidence()));
        }
        return deduplicateSlots(slots);
    }

    private String outputFormat(AnalysisIntent analysisIntent) {
        if (analysisIntent == null) {
            return "";
        }
        return switch (analysisIntent) {
            case DISTRIBUTION -> "distribution";
            case RANKING -> "ranking";
            case TREND -> "trend";
            case COMPARISON -> "comparison";
            case DETAIL -> "detail";
            case ANALYSIS -> "analysis";
            case TOTAL -> "";
        };
    }

    private ParsedQuestion applyRuleGuardrails(ParsedQuestion candidate,
                                               ParsedQuestion ruleParsedQuestion,
                                               UnderstandingSource source) {
        Map<IntentType, ParsedIntent> intents = new LinkedHashMap<>();
        for (ParsedIntent intent : candidate.intents()) {
            intents.put(intent.intentType(), intent);
        }
        if (candidate.understanding().needCitation()) {
            for (ParsedIntent intent : ruleParsedQuestion.intents()) {
                if (intent.intentType() == IntentType.LEGAL_ADVICE
                        || intent.intentType() == IntentType.POLICY_INTERPRETATION
                        || intent.intentType() == IntentType.BUSINESS_CONSULTATION) {
                    intents.putIfAbsent(intent.intentType(), intent);
                }
            }
        }
        if (candidate.understanding().needSuggestion()) {
            intents.putIfAbsent(IntentType.BUSINESS_CONSULTATION, new ParsedIntent(
                    IntentType.BUSINESS_CONSULTATION,
                    false,
                    "LLM识别需要处置建议",
                    Math.max(0.55D, candidate.confidence())
            ));
        }

        LinkedHashSet<UrbanScene> scenes = new LinkedHashSet<>(candidate.scenes());
        if (scenes.isEmpty()) {
            scenes.addAll(ruleParsedQuestion.scenes());
        }

        Map<String, ExtractedSlot> slots = new LinkedHashMap<>();
        for (ExtractedSlot slot : candidate.slots()) {
            slots.put(slot.slotType() + ":" + slot.normalizedValue(), slot);
        }
        for (ExtractedSlot slot : ruleParsedQuestion.slots()) {
            boolean missingSameType = slots.values().stream().noneMatch(existing -> existing.slotType() == slot.slotType());
            if (slot.mandatory() || missingSameType) {
                slots.putIfAbsent(slot.slotType() + ":" + slot.normalizedValue(), slot);
            }
        }

        List<ParsedIntent> intentList = List.copyOf(intents.values());
        List<ExtractedSlot> slotList = List.copyOf(slots.values());
        QuestionUnderstanding understanding = new QuestionUnderstanding(
                intentList.stream().map(ParsedIntent::intentType).map(QuestionType::fromIntentType).toList(),
                candidate.understanding().primaryQuestionType(),
                List.copyOf(scenes),
                candidate.understanding().dataIntent(),
                normalizeDataIntents(candidate.understanding()),
                normalizeKnowledgeIntents(candidate.understanding()),
                candidate.understanding().needCitation() || hasCitationIntent(intentList),
                candidate.understanding().needSuggestion() || intentList.stream().anyMatch(intent -> intent.intentType() == IntentType.BUSINESS_CONSULTATION),
                Math.max(candidate.confidence(), calculateConfidence(intentList, slotList)),
                source
        );
        return new ParsedQuestion(
                candidate.originalQuestion(),
                intentList,
                List.copyOf(scenes),
                slotList,
                understanding.confidence(),
                understanding
        );
    }

    private ParsedQuestion mergeParsedQuestions(ParsedQuestion left, ParsedQuestion right) {
        Map<IntentType, ParsedIntent> mergedIntents = new LinkedHashMap<>();
        for (ParsedIntent intent : left.intents()) {
            mergedIntents.put(intent.intentType(), intent);
        }
        for (ParsedIntent intent : right.intents()) {
            ParsedIntent existing = mergedIntents.get(intent.intentType());
            if (existing == null || intent.confidence() > existing.confidence()) {
                mergedIntents.put(intent.intentType(), intent);
            }
        }
        LinkedHashSet<UrbanScene> mergedScenes = new LinkedHashSet<>(left.scenes());
        mergedScenes.addAll(left.scenes());
        mergedScenes.addAll(right.scenes());

        Map<String, ExtractedSlot> mergedSlots = new LinkedHashMap<>();
        for (ExtractedSlot slot : left.slots()) {
            mergedSlots.put(slot.slotType() + ":" + slot.normalizedValue(), slot);
        }
        for (ExtractedSlot slot : right.slots()) {
            String key = slot.slotType() + ":" + slot.normalizedValue();
            ExtractedSlot existingSlot = mergedSlots.get(key);
            if (existingSlot == null || slot.confidence() > existingSlot.confidence()) {
                mergedSlots.put(key, slot);
            }
        }

        List<ParsedIntent> intentList = List.copyOf(mergedIntents.values());
        List<ExtractedSlot> slotList = List.copyOf(mergedSlots.values());
        double mergedConfidence = Math.max(left.confidence(), Math.max(right.confidence(), calculateConfidence(intentList, slotList)));
        return new ParsedQuestion(
                left.originalQuestion(),
                intentList,
                List.copyOf(mergedScenes),
                slotList,
                Math.min(0.99D, mergedConfidence),
                left.understanding()
        );
    }

    private boolean hasCitationIntent(List<ParsedIntent> intents) {
        return intents.stream().anyMatch(intent -> intent.intentType() == IntentType.LEGAL_ADVICE
                || intent.intentType() == IntentType.POLICY_INTERPRETATION
                || intent.intentType() == IntentType.BUSINESS_CONSULTATION);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private List<DataQueryIntent> normalizeDataIntents(QuestionUnderstanding understanding) {
        if (understanding == null) {
            return List.of();
        }
        List<DataQueryIntent> dataIntents = safeList(understanding.dataIntents());
        if (!dataIntents.isEmpty()) {
            return dataIntents;
        }
        return understanding.dataIntent() == null ? List.of() : List.of(understanding.dataIntent());
    }

    private List<KnowledgeIntent> normalizeKnowledgeIntents(QuestionUnderstanding understanding) {
        return understanding == null ? List.of() : safeList(understanding.knowledgeIntents()).stream()
                .filter(intent -> intent != null)
                .distinct()
                .toList();
    }
}