package com.example.urbanagent.agent.application.dto;

import com.example.urbanagent.query.application.dto.DataFragment;
import com.example.urbanagent.query.application.dto.DataStatement;
import com.example.urbanagent.query.application.dto.QueryCardView;

import java.util.List;

public record ComposedAnswer(
        String conclusion,
        String dataSection,
        String evidenceSection,
        String suggestionSection,
        String statementSection,
        String limitationSection,
        List<DataFragment> dataFragments,
        List<DataStatement> dataStatements,
        List<EvidenceRef> evidenceRefs,
        List<QueryCardView> queryCards
) {

    public String render() {
        return String.join(
                "\n\n",
                section("结论", conclusion),
                section("数据", dataSection),
                section("依据", evidenceSection),
                section("建议", suggestionSection),
                section("口径", statementSection),
                section("限制", limitationSection)
        ).trim();
    }

    private String section(String title, String content) {
        if (content == null || content.isBlank()) {
            return title + "：暂无";
        }
        return title + "：" + content.trim();
    }
}
