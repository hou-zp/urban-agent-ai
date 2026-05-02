package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.DataClaim;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据声明检测器。
 * 从答案文本中提取数据性结论，并关联到查询编号。
 */
@Component
public class DataClaimDetector {

    private static final List<PatternClaim> DATA_PATTERNS = List.of(
            // 数量类
            new PatternClaim(
                    Pattern.compile("(\\d+)\\s*(件|个|处|次|户|宗|条|笔)"),
                    DataClaim.ClaimType.COUNT
            ),
            // 趋势类
            new PatternClaim(
                    Pattern.compile("(上升|下降|增长|减少|增加|回落)(?:了)?(\\d+)\\s*%?"),
                    DataClaim.ClaimType.TREND
            ),
            new PatternClaim(
                    Pattern.compile("(环比|同比)(?:增长|上升|下降|减少)"),
                    DataClaim.ClaimType.COMPARISON
            ),
            // 排名类
            new PatternClaim(
                    Pattern.compile("(第一|第二|第三|第\\d+|最高|最低|最多|最少|排名)"),
                    DataClaim.ClaimType.RANKING
            ),
            // 状态类
            new PatternClaim(
                    Pattern.compile("(异常|超标|超期|待处理|已处理|办结|未闭环)"),
                    DataClaim.ClaimType.STATUS
            )
    );

    private static final Pattern QUERY_ID_PATTERN = Pattern.compile("[A-Z]-\\d{10,}");

    /**
     * 从答案文本中提取数据声明。
     */
    public List<ClaimWithContext> detectClaims(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<ClaimWithContext> claims = new ArrayList<>();
        for (PatternClaim pattern : DATA_PATTERNS) {
            Matcher matcher = pattern.pattern().matcher(text);
            int position = 0;
            while (matcher.find()) {
                String matched = matcher.group();
                int start = matcher.start();
                int end = matcher.end();
                // 避免重复
                if (start >= position) {
                    claims.add(new ClaimWithContext(
                            matched,
                            pattern.claimType(),
                            start,
                            end,
                            extractNearbyQueryId(text, end)
                    ));
                    position = end;
                }
            }
        }
        return claims;
    }

    /**
     * 提取声明附近的查询编号。
     */
    private String extractNearbyQueryId(String text, int afterPosition) {
        // 在声明后 50 个字符内查找查询编号
        int searchStart = afterPosition;
        int searchEnd = Math.min(afterPosition + 50, text.length());
        String nearby = text.substring(searchStart, searchEnd);

        Matcher matcher = QUERY_ID_PATTERN.matcher(nearby);
        if (matcher.find()) {
            return matcher.group();
        }

        // 也在声明前查找
        int beforeStart = Math.max(0, afterPosition - 80);
        int beforeEnd = afterPosition;
        String before = text.substring(beforeStart, beforeEnd);
        matcher = QUERY_ID_PATTERN.matcher(before);
        while (matcher.find()) {
            // 最后一个匹配
        }
        if (matcher.reset().find()) {
            String last = null;
            while (matcher.find()) {
                last = matcher.group();
            }
            if (last != null) {
                return last;
            }
        }

        return null;
    }

    /**
     * 从已知的查询编号列表中为声明分配关联。
     */
    public List<DataClaim> associateWithQueries(List<ClaimWithContext> claims,
                                               List<String> availableQueryIds) {
        return claims.stream()
                .map(claim -> {
                    String queryId = claim.queryId();
                    if (queryId == null && !availableQueryIds.isEmpty()) {
                        // 关联到最近的查询
                        queryId = availableQueryIds.get(0);
                    }
                    return new DataClaim(
                            claim.text(),
                            claim.claimType(),
                            queryId,
                            queryId != null
                    );
                })
                .toList();
    }

    /**
     * 检测文本是否包含数字性结论（用于快速判断）。
     */
    public boolean containsNumericConclusion(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (PatternClaim pattern : DATA_PATTERNS) {
            if (pattern.pattern().matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 声明及其上下文。
     */
    public record ClaimWithContext(
            String text,
            DataClaim.ClaimType claimType,
            int startPosition,
            int endPosition,
            String queryId
    ) {
        public boolean hasQueryId() {
            return queryId != null && !queryId.isBlank();
        }
    }

    private record PatternClaim(Pattern pattern, DataClaim.ClaimType claimType) {
    }
}