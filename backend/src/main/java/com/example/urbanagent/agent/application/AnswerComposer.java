package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.AnswerDraft;
import com.example.urbanagent.agent.application.dto.ComposedAnswer;
import com.example.urbanagent.agent.application.dto.DataClaim;
import com.example.urbanagent.agent.application.dto.EvidenceRef;
import com.example.urbanagent.agent.application.dto.ExtractedSlot;
import com.example.urbanagent.agent.application.dto.ParsedQuestion;
import com.example.urbanagent.agent.application.dto.SlotType;
import com.example.urbanagent.agent.application.dto.UrbanScene;
import com.example.urbanagent.query.application.dto.DataFragment;
import com.example.urbanagent.query.application.dto.DataStatement;
import com.example.urbanagent.query.application.dto.QueryAnswerView;
import com.example.urbanagent.query.application.dto.QueryCardView;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AnswerComposer {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"));
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+\\s*(件|个|处|次|%|百分比|排名|第一|第二|第三|上升|下降|环比|同比)");
    private static final List<String> DATA_KEYWORDS = List.of("数量", "多少", "趋势", "排名", "上升", "下降", "同比", "环比", "分布", "热点", "高发");

    public ComposedAnswer compose(ParsedQuestion parsedQuestion,
                                  QueryAnswerView queryAnswer,
                                  List<EvidenceRef> evidenceRefs) {
        List<QueryCardView> queryCards = queryAnswer.queryCards();
        List<DataStatement> dataStatements = queryAnswer.dataStatements();
        List<DataFragment> dataFragments = queryCards.stream()
                .map(QueryCardView::dataFragment)
                .toList();
        String conclusion = queryAnswer.answer();
        String dataSection = buildDataSection(queryCards);
        String evidenceSection = buildEvidenceSection(evidenceRefs);
        String suggestionSection = buildSuggestionSection(parsedQuestion, queryCards);
        String statementSection = buildStatementSection(dataStatements);
        String limitationSection = buildLimitationSection(queryAnswer.warnings(), dataStatements);
        return new ComposedAnswer(
                conclusion,
                dataSection,
                evidenceSection,
                suggestionSection,
                statementSection,
                limitationSection,
                dataFragments,
                dataStatements,
                evidenceRefs,
                queryCards
        );
    }

    /**
     * 生成结构化答案草稿（AnswerDraft）。
     * 用于支持六段式答案结构和数据声明追踪。
     */
    public AnswerDraft composeDraft(ParsedQuestion parsedQuestion,
                                     QueryAnswerView queryAnswer,
                                     List<EvidenceRef> evidenceRefs) {
        List<QueryCardView> queryCards = queryAnswer.queryCards();
        List<DataStatement> dataStatements = queryAnswer.dataStatements();

        // 1. 结论摘要
        String conclusion = buildConclusion(parsedQuestion, queryAnswer);

        // 2. 数据结果
        List<String> dataFindings = buildDataFindings(queryCards, queryAnswer);

        // 3. 政策法规依据
        List<String> policyFindings = buildPolicyFindings(evidenceRefs);
        List<String> lawFindings = buildLawFindings(evidenceRefs);

        // 4. 业务判断
        List<String> businessJudgements = buildBusinessJudgements(parsedQuestion, queryCards);

        // 5. 处置建议
        List<String> suggestions = buildSuggestions(parsedQuestion, queryCards);

        // 6. 口径、来源和风险提示
        List<String> limitations = buildLimitations(queryAnswer.warnings(), dataStatements);

        // 数据声明提取
        List<DataClaim> dataClaims = extractDataClaims(queryAnswer.answer(), queryCards);

        return AnswerDraft.builder()
                .conclusion(conclusion)
                .dataFindings(dataFindings)
                .policyFindings(policyFindings)
                .lawFindings(lawFindings)
                .businessJudgements(businessJudgements)
                .suggestions(suggestions)
                .limitations(limitations)
                .evidenceRefs(evidenceRefs)
                .dataClaims(dataClaims)
                .build();
    }

    private String buildConclusion(ParsedQuestion parsedQuestion, QueryAnswerView queryAnswer) {
        if (queryAnswer.answer() != null && !queryAnswer.answer().isBlank()) {
            return queryAnswer.answer();
        }
        if (!queryAnswer.queryCards().isEmpty()) {
            return queryAnswer.queryCards().stream()
                    .findFirst()
                    .map(card -> card.metricName() + "：" + card.resultSummary())
                    .orElse("暂无结论");
        }
        return "暂无有效结论";
    }

    private List<String> buildDataFindings(List<QueryCardView> queryCards, QueryAnswerView queryAnswer) {
        List<String> findings = new ArrayList<>();
        for (QueryCardView card : queryCards) {
            String metricName = card.metricName() == null || card.metricName().isBlank() ? "查询结果" : card.metricName();
            findings.add(metricName + "：" + card.resultSummary() + "（查询编号：" + card.queryId() + "）");
        }
        if (findings.isEmpty()) {
            findings.add("本次未形成可用数据结果。");
        }
        return findings;
    }

    private List<String> buildPolicyFindings(List<EvidenceRef> evidenceRefs) {
        return evidenceRefs.stream()
                .filter(ref -> "POLICY".equals(ref.sourceType()))
                .map(ref -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("《").append(ref.documentTitle()).append("》");
                    if (ref.sectionTitle() != null && !ref.sectionTitle().isBlank()) {
                        sb.append(" / ").append(ref.sectionTitle());
                    }
                    if (ref.issuingAuthority() != null && !ref.issuingAuthority().isBlank()) {
                        sb.append("（发文机关：").append(ref.issuingAuthority()).append("）");
                    }
                    return sb.toString();
                })
                .toList();
    }

    private List<String> buildLawFindings(List<EvidenceRef> evidenceRefs) {
        return evidenceRefs.stream()
                .filter(ref -> "LAW".equals(ref.sourceType()))
                .map(ref -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("《").append(ref.documentTitle()).append("》");
                    if (ref.articleNo() != null && !ref.articleNo().isBlank()) {
                        sb.append(" ").append(ref.articleNo());
                    }
                    if (ref.quote() != null && !ref.quote().isBlank()) {
                        sb.append("：").append(ref.quote().substring(0, Math.min(100, ref.quote().length())));
                    }
                    if (!ref.effectiveAtQueryTime()) {
                        sb.append("（注意：该法规可能已失效）");
                    }
                    return sb.toString();
                })
                .toList();
    }

    private List<String> buildBusinessJudgements(ParsedQuestion parsedQuestion, List<QueryCardView> queryCards) {
        List<String> judgements = new ArrayList<>();
        UrbanScene scene = parsedQuestion.scenes().stream().findFirst().orElse(null);
        if (scene == UrbanScene.CATERING_OIL_FUME && !queryCards.isEmpty()) {
            judgements.add("从数据分布看，问题主要集中在连续异常或未闭环点位。");
        }
        if (scene == UrbanScene.GARBAGE_OVERFLOW) {
            judgements.add("垃圾满溢问题多发于投放高峰期和清运力量薄弱区域。");
        }
        return judgements;
    }

    private List<String> buildSuggestions(ParsedQuestion parsedQuestion, List<QueryCardView> queryCards) {
        List<String> suggestions = new ArrayList<>();
        boolean rankingIntent = parsedQuestion.slotsOf(SlotType.OUTPUT_FORMAT).stream()
                .map(ExtractedSlot::normalizedValue)
                .anyMatch(value -> "ranking".equals(value) || "analysis".equals(value));
        boolean trendIntent = parsedQuestion.slotsOf(SlotType.OUTPUT_FORMAT).stream()
                .map(ExtractedSlot::normalizedValue)
                .anyMatch(value -> "trend".equals(value) || "comparison".equals(value));
        UrbanScene scene = parsedQuestion.scenes().stream().findFirst().orElse(null);

        if (scene == UrbanScene.CATERING_OIL_FUME) {
            suggestions.add("优先核查连续异常或未闭环点位，并结合街道分布安排复查。");
            suggestions.add("建立重复投诉台账，对设施不正常运行的商户进入执法流程。");
        } else if (scene == UrbanScene.GARBAGE_OVERFLOW) {
            suggestions.add("优先核查桶容、清运频次、投放时段和责任区边界。");
            suggestions.add("协调环卫作业单位增加清运频次或增设临时容器。");
        } else if (rankingIntent && !queryCards.isEmpty()) {
            suggestions.add("优先关注排名靠前的区域或对象，并结合属地处置力量安排专项排查。");
        } else if (trendIntent) {
            suggestions.add("建议继续按同一时间口径跟踪变化趋势，避免不同周期口径混用。");
        } else {
            suggestions.add("建议结合区域、时间范围和具体对象进一步下钻核查。");
        }
        return suggestions;
    }

    private List<String> buildLimitations(List<String> warnings, List<DataStatement> dataStatements) {
        List<String> limitations = new ArrayList<>();
        if (warnings != null && !warnings.isEmpty()) {
            limitations.addAll(warnings);
        }
        for (DataStatement statement : dataStatements) {
            if (statement.limitation() != null && !statement.limitation().isBlank()) {
                limitations.add(statement.limitation());
            }
        }
        if (limitations.isEmpty()) {
            limitations.add("结果基于当前权限和现有样本数据生成。");
        }
        return limitations;
    }

    private List<DataClaim> extractDataClaims(String answerText, List<QueryCardView> queryCards) {
        List<DataClaim> claims = new ArrayList<>();
        if (answerText == null || answerText.isBlank()) {
            return claims;
        }
        // 提取包含数字的声明
        if (NUMBER_PATTERN.matcher(answerText).find()) {
            // 关联到查询卡片
            for (QueryCardView card : queryCards) {
                if (card.queryId() != null && !card.queryId().isBlank()) {
                    claims.add(new DataClaim(
                            "数据查询结果",
                            DataClaim.ClaimType.COUNT,
                            card.queryId(),
                            true
                    ));
                }
            }
            // 检测趋势类声明
            if (answerText.contains("上升") || answerText.contains("下降") || answerText.contains("增长")) {
                String queryId = queryCards.isEmpty() ? null : queryCards.get(0).queryId();
                claims.add(new DataClaim("趋势结论", DataClaim.ClaimType.TREND, queryId, queryId != null));
            }
        }
        return claims;
    }

    private String buildDataSection(List<QueryCardView> queryCards) {
        if (queryCards.isEmpty()) {
            return "本次未形成可用数据结果。";
        }
        return queryCards.stream()
                .map(card -> {
                    String metricName = card.metricName() == null || card.metricName().isBlank() ? "查询结果" : card.metricName();
                    return metricName + "：" + card.resultSummary();
                })
                .reduce((left, right) -> left + "；" + right)
                .orElse("本次未形成可用数据结果。");
    }

    private String buildEvidenceSection(List<EvidenceRef> evidenceRefs) {
        if (evidenceRefs.isEmpty()) {
            return "本次回答未引用政策法规或业务依据。";
        }
        return evidenceRefs.stream()
                .map(ref -> ref.documentTitle() + (ref.sectionTitle() == null || ref.sectionTitle().isBlank() ? "" : " / " + ref.sectionTitle()))
                .reduce((left, right) -> left + "；" + right)
                .orElse("本次回答未引用政策法规或业务依据。");
    }

    private String buildSuggestionSection(ParsedQuestion parsedQuestion, List<QueryCardView> queryCards) {
        boolean rankingIntent = parsedQuestion.slotsOf(SlotType.OUTPUT_FORMAT).stream()
                .map(ExtractedSlot::normalizedValue)
                .anyMatch(value -> "ranking".equals(value) || "analysis".equals(value));
        boolean trendIntent = parsedQuestion.slotsOf(SlotType.OUTPUT_FORMAT).stream()
                .map(ExtractedSlot::normalizedValue)
                .anyMatch(value -> "trend".equals(value) || "comparison".equals(value));
        UrbanScene scene = parsedQuestion.scenes().stream().findFirst().orElse(null);
        if (scene == UrbanScene.CATERING_OIL_FUME) {
            return "优先核查连续异常或未闭环点位，并结合街道分布安排复查。";
        }
        if (rankingIntent && !queryCards.isEmpty()) {
            return "优先关注排名靠前的区域或对象，并结合属地处置力量安排专项排查。";
        }
        if (trendIntent) {
            return "建议继续按同一时间口径跟踪变化趋势，避免不同周期口径混用。";
        }
        return "建议结合区域、时间范围和具体对象进一步下钻核查。";
    }

    private String buildStatementSection(List<DataStatement> dataStatements) {
        if (dataStatements.isEmpty()) {
            return "暂无数据口径说明。";
        }
        return dataStatements.stream()
                .map(statement -> {
                    String updatedAt = statement.dataUpdatedAt() == null ? "未知时间" : TIME_FORMATTER.format(statement.dataUpdatedAt());
                    return (statement.metricName() == null || statement.metricName().isBlank() ? "查询结果" : statement.metricName())
                            + " 来源=" + nullSafe(statement.sourceSummary())
                            + "，范围=" + nullSafe(statement.scopeSummary())
                            + "，更新时间=" + updatedAt
                            + "，权限=" + nullSafe(statement.permissionRewrite());
                })
                .reduce((left, right) -> left + "；" + right)
                .orElse("暂无数据口径说明。");
    }

    private String buildLimitationSection(List<String> warnings, List<DataStatement> dataStatements) {
        String warningsText = warnings == null || warnings.isEmpty() ? "" : String.join("；", warnings);
        String statementLimitations = dataStatements.stream()
                .map(DataStatement::limitation)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
        if (warningsText.isBlank() && statementLimitations.isBlank()) {
            return "结果基于当前权限和现有样本数据生成。";
        }
        if (warningsText.isBlank()) {
            return statementLimitations;
        }
        if (statementLimitations.isBlank()) {
            return warningsText;
        }
        return warningsText + "；" + statementLimitations;
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "未说明" : value;
    }
}
