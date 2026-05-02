package com.example.urbanagent.agent.application.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnswerDraftTest {

    @Test
    void shouldBuildWithAllFields() {
        List<EvidenceRef> evidenceRefs = List.of(
                EvidenceRef.fromCitation(
                        "ev-001", "doc-001", "餐饮油烟治理方案",
                        "第一章 / 第三条", "市城管局",
                        LocalDate.of(2024, 1, 1), null
                )
        );
        List<DataClaim> dataClaims = List.of(
                new DataClaim("A区4月油烟投诉较3月上升15%", DataClaim.ClaimType.TREND, "Q-001", true)
        );

        AnswerDraft draft = AnswerDraft.builder()
                .conclusion("A区餐饮油烟投诉呈上升趋势")
                .dataFindings(List.of("4月投诉量 87 件，环比上升 15%"))
                .policyFindings(List.of("根据《餐饮油烟治理方案》第三条"))
                .lawFindings(List.of())
                .businessJudgements(List.of("问题集中在柯桥街道"))
                .suggestions(List.of("优先核查高发点位"))
                .limitations(List.of("结果基于已接入数据"))
                .evidenceRefs(evidenceRefs)
                .dataClaims(dataClaims)
                .build();

        assertNotNull(draft);
        assertEquals("A区餐饮油烟投诉呈上升趋势", draft.conclusion());
        assertEquals(1, draft.dataFindings().size());
        assertEquals(1, draft.evidenceRefs().size());
        assertEquals(1, draft.dataClaims().size());
        assertNotNull(draft.createdAt());
    }

    @Test
    void shouldDetectUnsupportedClaims() {
        List<DataClaim> dataClaims = List.of(
                new DataClaim("案件数 100 件", DataClaim.ClaimType.COUNT, "Q-001", true),
                new DataClaim("全市最高", DataClaim.ClaimType.RANKING, null, false)  // 无 queryId
        );

        AnswerDraft draft = AnswerDraft.builder()
                .conclusion("测试结论")
                .dataClaims(dataClaims)
                .build();

        List<DataClaim> unsupported = draft.unsupportedClaims();
        assertEquals(1, unsupported.size());
        assertEquals("全市最高", unsupported.get(0).claimText());
    }

    @Test
    void shouldDetectUnattributedLegalContent() {
        AnswerDraft draftWithEvidence = AnswerDraft.builder()
                .conclusion("测试")
                .policyFindings(List.of("政策依据"))
                .evidenceRefs(List.of(EvidenceRef.fromCitation(
                        "ev-001", "doc-001", "测试文档", null, null, null, null)))
                .build();

        assertFalse(draftWithEvidence.hasUnattributedLegalContent());

        AnswerDraft draftWithoutEvidence = AnswerDraft.builder()
                .conclusion("测试")
                .policyFindings(List.of())
                .lawFindings(List.of())
                .evidenceRefs(List.of())
                .build();

        assertTrue(draftWithoutEvidence.hasUnattributedLegalContent());
    }

    @Test
    void dataClaimShouldValidateCorrectly() {
        DataClaim validClaim = new DataClaim("100件", DataClaim.ClaimType.COUNT, "Q-001", true);
        assertTrue(validClaim.hasQueryId());
        assertTrue(validClaim.isVerifiable());

        DataClaim invalidClaim = new DataClaim("全市最多", DataClaim.ClaimType.RANKING, null, false);
        assertFalse(invalidClaim.hasQueryId());
        assertFalse(invalidClaim.isVerifiable());

        DataClaim unsupportedClaim = new DataClaim("100件", DataClaim.ClaimType.COUNT, "Q-001", false);
        assertTrue(unsupportedClaim.hasQueryId());
        assertFalse(unsupportedClaim.isVerifiable());
    }

    @Test
    void evidenceRefShouldDetectValidity() {
        EvidenceRef validRef = EvidenceRef.fromCitation(
                "ev-001", "doc-001", "测试", null, null,
                LocalDate.of(2024, 1, 1), null
        );
        assertTrue(validRef.effectiveAtQueryTime());

        // 过期证据
        EvidenceRef expiredRef = EvidenceRef.builder()
                .evidenceId("ev-002")
                .sourceId("doc-002")
                .sourceType("LAW")
                .documentTitle("过期法规")
                .effectiveFrom(LocalDate.of(2020, 1, 1))
                .effectiveTo(LocalDate.of(2023, 12, 31))
                .effectiveAtQueryTime(false)
                .retrievedAt(Instant.now())
                .build();
        assertFalse(expiredRef.effectiveAtQueryTime());
    }

    @Test
    void evidenceRefShouldGenerateShortDescription() {
        EvidenceRef ref = EvidenceRef.builder()
                .evidenceId("ev-001")
                .sourceId("doc-001")
                .sourceType("POLICY")
                .documentTitle("餐饮油烟治理专项方案")
                .issuingAuthority("市城管局")
                .docNo("城管发[2024]1号")
                .sectionTitle("第三章 处置要求")
                .articleNo("第十五条")
                .quote("油烟排放标准")
                .publishDate(LocalDate.of(2024, 1, 1))
                .regionCode("310100")
                .effectiveAtQueryTime(true)
                .retrievedAt(Instant.now())
                .build();

        String shortDesc = ref.shortDescription();
        assertTrue(shortDesc.contains("餐饮油烟治理专项方案"));
        assertTrue(shortDesc.contains("第三章 处置要求"));
        assertTrue(shortDesc.contains("第十五条"));
    }
}