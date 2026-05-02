package com.example.urbanagent.agent.tool;

import com.example.urbanagent.query.application.Nl2SqlService;
import com.example.urbanagent.query.application.OrganizationDimensionTranslator;
import com.example.urbanagent.query.application.PermissionRewrittenSql;
import com.example.urbanagent.query.application.ReadonlySqlQueryService;
import com.example.urbanagent.query.application.SqlPermissionService;
import com.example.urbanagent.query.application.SqlValidationService;
import com.example.urbanagent.query.application.ValidatedSql;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class QueryAgentTools {

    private final Nl2SqlService nl2SqlService;
    private final SqlValidationService sqlValidationService;
    private final SqlPermissionService sqlPermissionService;
    private final ReadonlySqlQueryService readonlySqlQueryService;
    private final OrganizationDimensionTranslator organizationDimensionTranslator;

    public QueryAgentTools(Nl2SqlService nl2SqlService,
                           SqlValidationService sqlValidationService,
                           SqlPermissionService sqlPermissionService,
                           ReadonlySqlQueryService readonlySqlQueryService,
                           OrganizationDimensionTranslator organizationDimensionTranslator) {
        this.nl2SqlService = nl2SqlService;
        this.sqlValidationService = sqlValidationService;
        this.sqlPermissionService = sqlPermissionService;
        this.readonlySqlQueryService = readonlySqlQueryService;
        this.organizationDimensionTranslator = organizationDimensionTranslator;
    }

    @Tool(name = "nl2sql_generate", description = "根据自然语言问题生成候选 SQL")
    @org.springframework.ai.tool.annotation.Tool(name = "nl2sql_generate", description = "根据自然语言问题生成候选 SQL")
    public String nl2sqlGenerate(@ToolParam(name = "question", description = "用户的问数问题")
                                 @org.springframework.ai.tool.annotation.ToolParam(description = "用户的问数问题", required = true)
                                 String question) {
        return nl2SqlService.generate(question).candidateSql();
    }

    @Tool(name = "sql_validate", description = "校验 SQL 是否满足只读和授权要求")
    @org.springframework.ai.tool.annotation.Tool(name = "sql_validate", description = "校验 SQL 是否满足只读和授权要求")
    public String sqlValidate(@ToolParam(name = "sql", description = "待校验 SQL")
                              @org.springframework.ai.tool.annotation.ToolParam(description = "待校验 SQL", required = true)
                              String sql) {
        ValidatedSql validatedSql = sqlValidationService.validate(sql);
        PermissionRewrittenSql rewrittenSql = sqlPermissionService.rewrite(validatedSql);
        return "validated_sql: " + rewrittenSql.sql() + "\npermission_rewrite: " + rewrittenSql.summary();
    }

    @Tool(name = "readonly_sql_query", description = "执行通过校验的只读 SQL 查询")
    @org.springframework.ai.tool.annotation.Tool(name = "readonly_sql_query", description = "执行通过校验的只读 SQL 查询")
    public String readonlySqlQuery(@ToolParam(name = "sql", description = "通过校验的 SQL")
                                   @org.springframework.ai.tool.annotation.ToolParam(description = "通过校验的 SQL", required = true)
                                   String sql) {
        ValidatedSql validatedSql = sqlValidationService.validate(sql);
        PermissionRewrittenSql rewrittenSql = sqlPermissionService.rewrite(validatedSql);
        List<Map<String, Object>> rows = organizationDimensionTranslator.translate(readonlySqlQueryService.execute(rewrittenSql.sql()));
        return "rows=" + rows.size() + ", data=" + rows.stream().limit(5).toList();
    }
}
