package com.example.urbanagent.agent.application.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 证据引用结构。
 * 用于追踪答案中政策法规、业务规则的来源，支持答案可追溯验证。
 */
public record EvidenceRef(
        String evidenceId,        // 证据唯一标识
        String sourceId,         // 来源文档 ID（policy_id / law_id / business_rule_id）
        String sourceType,       // 来源类型：POLICY / LAW / BUSINESS_RULE
        String documentTitle,    // 文档标题
        String issuingAuthority, // 发文机关
        String docNo,           // 文号
        String sectionTitle,    // 章节标题（如"第十五条"）
        String articleNo,       // 条款号
        String quote,           // 引用原文（可选，最长 500 字符）
        String sourceUrl,       // 来源 URL（可选）
        LocalDate publishDate,  // 发布日期
        LocalDate effectiveFrom,// 生效日期
        LocalDate effectiveTo,  // 失效日期（null 表示未失效）
        String regionCode,      // 适用地区编码
        boolean effectiveAtQueryTime, // 查询时刻是否有效
        String queryId,         // 关联的数据查询编号（可选）
        Instant retrievedAt     // 检索时间
) {
    /**
     * 兼容旧版 7 参数构造器。
     * 用于从 MessageCitationView 转换。
     */
    public static EvidenceRef fromCitation(String evidenceId, String documentId,
                                           String title, String section,
                                           String org, LocalDate effectiveFrom, LocalDate effectiveTo) {
        return new EvidenceRef(
                evidenceId,
                documentId,
                null,          // sourceType
                title,
                org,
                null,          // docNo
                section,
                null,          // articleNo
                null,          // quote
                null,          // sourceUrl
                null,          // publishDate
                effectiveFrom,
                effectiveTo,
                null,          // regionCode
                true,          // effectiveAtQueryTime
                null,          // queryId
                Instant.now()  // retrievedAt
        );
    }

    /**
     * 检查证据是否在查询时刻有效。
     */
    public boolean isValidAt(Instant queryTime) {
        if (effectiveTo != null && queryTime != null) {
            return effectiveTo.isAfter(java.time.LocalDate.from(queryTime.atZone(java.time.ZoneId.of("Asia/Shanghai"))));
        }
        return effectiveAtQueryTime;
    }

    /**
     * 获取证据的简短描述，用于答案展示。
     */
    public String shortDescription() {
        StringBuilder sb = new StringBuilder();
        if (documentTitle != null && !documentTitle.isBlank()) {
            sb.append(documentTitle);
        }
        if (sectionTitle != null && !sectionTitle.isBlank()) {
            sb.append(" / ").append(sectionTitle);
        }
        if (articleNo != null && !articleNo.isBlank()) {
            sb.append(" (").append(articleNo).append(")");
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String evidenceId;
        private String sourceId;
        private String sourceType;
        private String documentTitle;
        private String issuingAuthority;
        private String docNo;
        private String sectionTitle;
        private String articleNo;
        private String quote;
        private String sourceUrl;
        private LocalDate publishDate;
        private LocalDate effectiveFrom;
        private LocalDate effectiveTo;
        private String regionCode;
        private boolean effectiveAtQueryTime = true;
        private String queryId;
        private Instant retrievedAt;

        public Builder evidenceId(String evidenceId) { this.evidenceId = evidenceId; return this; }
        public Builder sourceId(String sourceId) { this.sourceId = sourceId; return this; }
        public Builder sourceType(String sourceType) { this.sourceType = sourceType; return this; }
        public Builder documentTitle(String documentTitle) { this.documentTitle = documentTitle; return this; }
        public Builder issuingAuthority(String issuingAuthority) { this.issuingAuthority = issuingAuthority; return this; }
        public Builder docNo(String docNo) { this.docNo = docNo; return this; }
        public Builder sectionTitle(String sectionTitle) { this.sectionTitle = sectionTitle; return this; }
        public Builder articleNo(String articleNo) { this.articleNo = articleNo; return this; }
        public Builder quote(String quote) { this.quote = quote; return this; }
        public Builder sourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; return this; }
        public Builder publishDate(LocalDate publishDate) { this.publishDate = publishDate; return this; }
        public Builder effectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; return this; }
        public Builder effectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; return this; }
        public Builder regionCode(String regionCode) { this.regionCode = regionCode; return this; }
        public Builder effectiveAtQueryTime(boolean effectiveAtQueryTime) { this.effectiveAtQueryTime = effectiveAtQueryTime; return this; }
        public Builder queryId(String queryId) { this.queryId = queryId; return this; }
        public Builder retrievedAt(Instant retrievedAt) { this.retrievedAt = retrievedAt; return this; }

        public EvidenceRef build() {
            return new EvidenceRef(
                    evidenceId, sourceId, sourceType, documentTitle,
                    issuingAuthority, docNo, sectionTitle, articleNo,
                    quote, sourceUrl, publishDate, effectiveFrom, effectiveTo,
                    regionCode, effectiveAtQueryTime, queryId,
                    retrievedAt != null ? retrievedAt : Instant.now()
            );
        }
    }
}
