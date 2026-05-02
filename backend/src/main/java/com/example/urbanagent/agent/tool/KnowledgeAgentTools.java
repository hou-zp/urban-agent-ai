package com.example.urbanagent.agent.tool;

import com.example.urbanagent.knowledge.application.KnowledgeSearchService;
import com.example.urbanagent.knowledge.application.dto.KnowledgeSearchHit;
import com.example.urbanagent.knowledge.domain.KnowledgeCategory;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KnowledgeAgentTools {

    private final KnowledgeSearchService knowledgeSearchService;

    public KnowledgeAgentTools(KnowledgeSearchService knowledgeSearchService) {
        this.knowledgeSearchService = knowledgeSearchService;
    }

    @Tool(name = "knowledge_search", description = "检索城管政策和业务知识，返回相关原文片段")
    @org.springframework.ai.tool.annotation.Tool(name = "knowledge_search", description = "检索城管政策和业务知识，返回相关原文片段")
    public String knowledgeSearch(@ToolParam(name = "query", description = "用户问题或检索关键词")
                                  @org.springframework.ai.tool.annotation.ToolParam(description = "用户问题或检索关键词", required = true)
                                  String query) {
        return renderHits(knowledgeSearchService.search(query, null, 3));
    }

    @Tool(name = "law_clause_search", description = "检索法规和条款依据，返回法规原文片段")
    @org.springframework.ai.tool.annotation.Tool(name = "law_clause_search", description = "检索法规和条款依据，返回法规原文片段")
    public String lawClauseSearch(@ToolParam(name = "query", description = "法规问题或条款关键词")
                                  @org.springframework.ai.tool.annotation.ToolParam(description = "法规问题或条款关键词", required = true)
                                  String query) {
        return renderHits(knowledgeSearchService.search(query, KnowledgeCategory.LAW, 3));
    }

    private String renderHits(List<KnowledgeSearchHit> hits) {
        if (hits.isEmpty()) {
            return "未检索到可用知识。";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < hits.size(); index++) {
            KnowledgeSearchHit hit = hits.get(index);
            AgentExecutionContextHolder.addCitation(hit.toCitation());
            builder.append(index + 1)
                    .append(". 文档：")
                    .append(hit.documentTitle());
            if (hit.documentNumber() != null && !hit.documentNumber().isBlank()) {
                builder.append("（").append(hit.documentNumber()).append("）");
            }
            if (hit.sectionTitle() != null && !hit.sectionTitle().isBlank()) {
                builder.append("\n   位置：").append(hit.sectionTitle());
            }
            builder.append("\n   摘要：").append(hit.content()).append('\n');
        }
        return builder.toString().trim();
    }
}
