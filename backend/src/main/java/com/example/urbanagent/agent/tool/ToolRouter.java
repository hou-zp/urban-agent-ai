package com.example.urbanagent.agent.tool;

import io.agentscope.core.tool.Toolkit;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ToolRouter {

    private final Map<String, ToolRegistration> registrations;

    public ToolRouter(KnowledgeAgentTools knowledgeAgentTools,
                      DataCatalogAgentTools dataCatalogAgentTools,
                      QueryAgentTools queryAgentTools,
                      BusinessRecordAgentTools businessRecordAgentTools,
                      RiskAgentTools riskAgentTools) {
        Map<String, ToolRegistration> items = new LinkedHashMap<>();
        register(items, new ToolRegistration(
                "knowledge_search",
                "检索城管政策和业务知识，返回与问题相关的原文片段",
                Set.of("knowledge:read"),
                ToolRiskLevel.LOW,
                knowledgeAgentTools
        ));
        register(items, new ToolRegistration(
                "law_clause_search",
                "检索法规和条款依据，返回法规名称、条款摘要和原文片段",
                Set.of("knowledge:read", "law:read"),
                ToolRiskLevel.MEDIUM,
                knowledgeAgentTools
        ));
        register(items, new ToolRegistration(
                "data_catalog_search",
                "查询已授权的数据目录、指标和字段定义",
                Set.of("catalog:read"),
                ToolRiskLevel.LOW,
                dataCatalogAgentTools
        ));
        register(items, new ToolRegistration(
                "nl2sql_generate",
                "根据自然语言问题生成候选 SQL",
                Set.of("query:preview"),
                ToolRiskLevel.MEDIUM,
                queryAgentTools
        ));
        register(items, new ToolRegistration(
                "sql_validate",
                "校验 SQL 是否满足只读和授权要求",
                Set.of("query:preview"),
                ToolRiskLevel.MEDIUM,
                queryAgentTools
        ));
        register(items, new ToolRegistration(
                "readonly_sql_query",
                "执行通过校验的只读 SQL 查询",
                Set.of("query:execute"),
                ToolRiskLevel.HIGH,
                queryAgentTools
        ));
        register(items, new ToolRegistration(
                "business_record_query",
                "查询案件、商户、地块、点位或工单明细，并自动执行权限过滤和敏感字段脱敏",
                Set.of("record:read"),
                ToolRiskLevel.HIGH,
                businessRecordAgentTools
        ));
        register(items, new ToolRegistration(
                "risk_assess",
                "评估问题是否涉及处罚、强制措施、拆除或复议诉讼等高风险事项",
                Set.of("risk:assess"),
                ToolRiskLevel.MEDIUM,
                riskAgentTools
        ));
        register(items, new ToolRegistration(
                "user_confirm",
                "请求用户对敏感动作进行确认",
                Set.of("agent:confirm"),
                ToolRiskLevel.MEDIUM,
                riskAgentTools
        ));
        this.registrations = Map.copyOf(items);
    }

    public List<ToolRegistration> list() {
        return registrations.values().stream().toList();
    }

    public Toolkit createToolkit() {
        Toolkit toolkit = new Toolkit();
        registrations.values()
                .stream()
                .map(ToolRegistration::toolBean)
                .distinct()
                .forEach(toolkit::registerTool);
        return toolkit;
    }

    private void register(Map<String, ToolRegistration> items, ToolRegistration registration) {
        items.put(registration.name(), registration);
    }
}