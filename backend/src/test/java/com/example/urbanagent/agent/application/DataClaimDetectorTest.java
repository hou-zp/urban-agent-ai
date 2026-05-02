package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.DataClaim;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class DataClaimDetectorTest {

    private final DataClaimDetector detector = new DataClaimDetector();

    @Test
    void shouldDetectCountClaims() {
        // 使用明确的"数字+量词"格式，直接匹配
        String text = "本月共发现 87 件垃圾满溢案件";
        List<DataClaimDetector.ClaimWithContext> claims = detector.detectClaims(text);

        assertFalse(claims.isEmpty(), "应该检测到数据声明");
        // 验证能检测到数量类声明
        assertTrue(claims.stream().anyMatch(c -> c.claimType() == DataClaim.ClaimType.COUNT),
                "应该检测到数量类声明，实际检测到: " + claims);
    }

    @Test
    void shouldDetectRankingClaims() {
        String text = "柯桥街道排名第一，越城街道排名第二";
        List<DataClaimDetector.ClaimWithContext> claims = detector.detectClaims(text);

        assertFalse(claims.isEmpty());
        assertTrue(claims.stream().allMatch(c -> c.claimType() == DataClaim.ClaimType.RANKING));
    }

    @Test
    void shouldDetectStatusClaims() {
        String text = "当前有 10 件异常案件，5 件已处理";
        List<DataClaimDetector.ClaimWithContext> claims = detector.detectClaims(text);

        assertFalse(claims.isEmpty());
        assertTrue(claims.stream().anyMatch(c -> c.claimType() == DataClaim.ClaimType.STATUS));
    }

    @Test
    void shouldReturnEmptyForNullText() {
        assertTrue(detector.detectClaims(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyForBlankText() {
        assertTrue(detector.detectClaims("").isEmpty());
        assertTrue(detector.detectClaims("   ").isEmpty());
    }

    @Test
    void shouldDetectNumericConclusion() {
        assertTrue(detector.containsNumericConclusion("4月投诉 87 件"));
        assertTrue(detector.containsNumericConclusion("同比上升 15%"));
        assertTrue(detector.containsNumericConclusion("排名第一"));
        assertFalse(detector.containsNumericConclusion("如何处理乱堆物料"));
        assertFalse(detector.containsNumericConclusion("请按流程处置"));
    }

    @Test
    void shouldAssociateClaimsWithQueryIds() {
        List<DataClaimDetector.ClaimWithContext> claims = List.of(
                new DataClaimDetector.ClaimWithContext("87 件", DataClaim.ClaimType.COUNT, 10, 13, null)
        );
        List<String> availableQueryIds = List.of("Q-001", "Q-002");

        List<DataClaim> dataClaims = detector.associateWithQueries(claims, availableQueryIds);
        assertEquals(1, dataClaims.size());
        assertEquals("Q-001", dataClaims.get(0).queryId());
        assertTrue(dataClaims.get(0).supported());
    }

    @Test
    void shouldPreserveExistingQueryId() {
        List<DataClaimDetector.ClaimWithContext> claims = List.of(
                new DataClaimDetector.ClaimWithContext("上升 15%", DataClaim.ClaimType.TREND, 5, 12, "Q-002")
        );

        List<DataClaim> dataClaims = detector.associateWithQueries(claims, List.of("Q-001"));
        assertEquals("Q-002", dataClaims.get(0).queryId());
    }

    @Test
    void claimWithContextShouldValidateQueryId() {
        DataClaimDetector.ClaimWithContext withQueryId = new DataClaimDetector.ClaimWithContext(
                "87 件", DataClaim.ClaimType.COUNT, 0, 5, "Q-001"
        );
        assertTrue(withQueryId.hasQueryId());

        DataClaimDetector.ClaimWithContext withoutQueryId = new DataClaimDetector.ClaimWithContext(
                "上升", DataClaim.ClaimType.TREND, 0, 3, null
        );
        assertFalse(withoutQueryId.hasQueryId());
    }
}