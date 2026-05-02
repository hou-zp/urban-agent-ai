package com.example.urbanagent.query.application;

import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.example.urbanagent.query.domain.DataTable;
import com.example.urbanagent.query.repository.DataTableRepository;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Service;

@Service
public class SqlPermissionService {

    private final DataTableRepository dataTableRepository;
    private final QueryAccessPolicyService queryAccessPolicyService;

    public SqlPermissionService(DataTableRepository dataTableRepository,
                                QueryAccessPolicyService queryAccessPolicyService) {
        this.dataTableRepository = dataTableRepository;
        this.queryAccessPolicyService = queryAccessPolicyService;
    }

    public PermissionRewrittenSql rewrite(ValidatedSql validatedSql) {
        UserContext userContext = UserContextHolder.get();
        DataTable dataTable = dataTableRepository.findByTableNameAndEnabledTrue(validatedSql.tableName())
                .orElseThrow(() -> new BusinessException(ErrorCode.SQL_PERMISSION_DENIED, "查询表未授权"));
        if (!queryAccessPolicyService.canAccessRegion(dataTable.getRegionCode(), userContext)) {
            throw new BusinessException(ErrorCode.SQL_PERMISSION_DENIED, "当前区域无权访问该数据表");
        }
        if ("ADMIN".equalsIgnoreCase(userContext.role())) {
            return new PermissionRewrittenSql(validatedSql.sql(), "管理员无需追加区域过滤");
        }
        try {
            Select select = (Select) CCJSqlParserUtil.parse(validatedSql.sql());
            PlainSelect plainSelect = select.getPlainSelect();
            Expression regionFilter = CCJSqlParserUtil.parseExpression("region_code = '" + userContext.region() + "'");
            if (plainSelect.getWhere() == null) {
                plainSelect.setWhere(regionFilter);
            } else {
                plainSelect.setWhere(new AndExpression(plainSelect.getWhere(), regionFilter));
            }
            return new PermissionRewrittenSql(select.toString(), "已追加区域过滤 region_code = '" + userContext.region() + "'");
        } catch (JSQLParserException ex) {
            throw new BusinessException(ErrorCode.SQL_VALIDATION_FAILED, ex.getMessage());
        }
    }

}
