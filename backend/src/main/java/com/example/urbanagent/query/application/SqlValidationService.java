package com.example.urbanagent.query.application;

import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.example.urbanagent.query.domain.DataTable;
import com.example.urbanagent.query.repository.DataTableRepository;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SqlValidationService {

    private static final long MAX_LIMIT = 200L;
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    private static final Set<String> SQL_KEYWORDS = Set.of(
            "as", "case", "when", "then", "else", "end", "null", "true", "false",
            "and", "or", "not", "in", "is", "like", "between", "distinct",
            "desc", "asc"
    );
    private static final Set<String> ALLOWED_FUNCTIONS = Set.of("sum", "count", "max", "min", "avg", "coalesce", "round");

    private final DataTableRepository dataTableRepository;
    private final QueryAccessPolicyService queryAccessPolicyService;

    public SqlValidationService(DataTableRepository dataTableRepository,
                                QueryAccessPolicyService queryAccessPolicyService) {
        this.dataTableRepository = dataTableRepository;
        this.queryAccessPolicyService = queryAccessPolicyService;
    }

    public ValidatedSql validate(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof PlainSelect plainSelect)) {
                throw new BusinessException(ErrorCode.SQL_VALIDATION_FAILED, "只允许执行 SELECT 语句");
            }
            if (plainSelect.getJoins() != null && !plainSelect.getJoins().isEmpty()) {
                throw new BusinessException(ErrorCode.SQL_VALIDATION_FAILED, "MVP 不支持 JOIN 查询");
            }

            List<String> tables = new TablesNamesFinder().getTableList(statement);
            if (tables.size() != 1) {
                throw new BusinessException(ErrorCode.SQL_VALIDATION_FAILED, "只允许查询单表");
            }
            String tableName = tables.get(0);
            DataTable table = dataTableRepository.findByTableNameAndEnabledTrue(tableName)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SQL_PERMISSION_DENIED, "查询表未授权"));

            validateSelectedFields(plainSelect, table);
            validateDatePredicate(plainSelect, table);
            applyLimit(plainSelect);
            return new ValidatedSql(table.getTableName(), plainSelect.toString());
        } catch (JSQLParserException ex) {
            throw new BusinessException(ErrorCode.SQL_VALIDATION_FAILED, ex.getMessage());
        }
    }

    private void validateSelectedFields(PlainSelect select, DataTable table) {
        Set<String> allFields = queryAccessPolicyService.allFieldNames(table.getId());
        Set<String> visibleFields = queryAccessPolicyService.visibleFieldNames(table.getId(), UserContextHolder.get());
        boolean admin = "ADMIN".equalsIgnoreCase(UserContextHolder.get().role());
        for (SelectItem<?> selectItem : select.getSelectItems()) {
            String itemText = selectItem.toString().toLowerCase(Locale.ROOT);
            if (!admin && selectItem.getExpression() instanceof AllColumns) {
                throw new BusinessException(ErrorCode.SQL_PERMISSION_DENIED, "非管理员不允许查询全部字段");
            }
            validateFunctions(itemText);
            validateFieldPermissions(itemText, allFields, visibleFields, admin);
        }
        validateClausePermissions(select.getWhere() == null ? null : select.getWhere().toString(), allFields, visibleFields, admin);
        validateClausePermissions(select.getHaving() == null ? null : select.getHaving().toString(), allFields, visibleFields, admin);
        validateClausePermissions(groupByText(select.getGroupBy()), allFields, visibleFields, admin);
        validateClausePermissions(orderByText(select.getOrderByElements()), allFields, visibleFields, admin);
    }

    private void validateDatePredicate(PlainSelect select, DataTable table) {
        if (!table.getTableName().startsWith("fact_")) {
            return;
        }
        Set<String> dateFields = queryAccessPolicyService.dateFieldNames(table.getId());
        if (dateFields.isEmpty()) {
            return;
        }
        if (select.getWhere() == null) {
            throw new BusinessException(ErrorCode.SQL_VALIDATION_FAILED, "事实表查询必须包含时间范围条件");
        }
        String whereText = select.getWhere().toString().toLowerCase(Locale.ROOT);
        for (String dateField : dateFields) {
            if (hasDatePredicate(whereText, dateField)) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.SQL_VALIDATION_FAILED, "事实表查询必须按日期字段过滤");
    }

    private void validateClausePermissions(String clauseText,
                                           Set<String> allFields,
                                           Set<String> visibleFields,
                                           boolean admin) {
        if (clauseText == null || clauseText.isBlank()) {
            return;
        }
        String normalizedClause = clauseText.toLowerCase(Locale.ROOT);
        validateFunctions(normalizedClause);
        validateFieldPermissions(normalizedClause, allFields, visibleFields, admin);
    }

    private void validateFieldPermissions(String clauseText,
                                          Set<String> allFields,
                                          Set<String> visibleFields,
                                          boolean admin) {
        if (admin) {
            return;
        }
        for (String identifier : identifiers(clauseText)) {
            if (!allFields.contains(identifier) || visibleFields.contains(identifier)) {
                continue;
            }
            throw new BusinessException(ErrorCode.SQL_PERMISSION_DENIED, "字段无访问权限: " + identifier);
        }
    }

    private void validateFunctions(String selectItemText) {
        Matcher matcher = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(").matcher(selectItemText);
        while (matcher.find()) {
            String functionName = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!ALLOWED_FUNCTIONS.contains(functionName)) {
                throw new BusinessException(ErrorCode.SQL_VALIDATION_FAILED, "不支持的函数: " + functionName);
            }
        }
    }

    private Set<String> identifiers(String text) {
        Set<String> identifiers = new HashSet<>();
        Matcher matcher = IDENTIFIER_PATTERN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group().toLowerCase(Locale.ROOT);
            if (SQL_KEYWORDS.contains(token)) {
                continue;
            }
            identifiers.add(token);
        }
        return identifiers;
    }

    private boolean hasDatePredicate(String whereText, String dateField) {
        String escapedDateField = Pattern.quote(dateField.toLowerCase(Locale.ROOT));
        return Pattern.compile("\\b" + escapedDateField + "\\b\\s+between\\b").matcher(whereText).find()
                || Pattern.compile("\\b" + escapedDateField + "\\b\\s*(=|>=|<=|>|<)").matcher(whereText).find();
    }

    private String groupByText(GroupByElement groupByElement) {
        return groupByElement == null ? null : groupByElement.toString();
    }

    private String orderByText(List<OrderByElement> orderByElements) {
        return orderByElements == null || orderByElements.isEmpty() ? null : orderByElements.toString();
    }

    private void applyLimit(PlainSelect select) {
        Limit limit = select.getLimit();
        if (limit == null || limit.getRowCount() == null) {
            select.setLimit(new Limit().withRowCount(new LongValue(MAX_LIMIT)));
            return;
        }
        LongValue rowCount = limit.getRowCount(LongValue.class);
        if (rowCount == null) {
            throw new BusinessException(ErrorCode.SQL_VALIDATION_FAILED, "limit 必须为明确数值");
        }
        if (rowCount.getValue() > MAX_LIMIT) {
            limit.setRowCount(new LongValue(MAX_LIMIT));
        }
    }
}
