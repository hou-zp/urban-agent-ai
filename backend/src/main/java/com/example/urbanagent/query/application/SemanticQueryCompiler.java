package com.example.urbanagent.query.application;

import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.example.urbanagent.query.config.AllowedSqlObjects;
import com.example.urbanagent.query.domain.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义 SQL 编译器。
 *
 * <p>将结构化意图（指标定义 + 时间范围 + 维度 + 区域）编译为受控 SQL。
 * 编译过程经过三层防护：
 * <ol>
 *   <li>表名白名单过滤（{@link AllowedSqlObjects}）</li>
 *   <li>列名 / 维度白名单过滤（{@link AllowedSqlObjects}）</li>
 *   <li>权限改写（{@link SqlPermissionService}）</li>
 * </ol>
 *
 * <p>本类是统一入口，不直接执行 SQL；
 * 编译结果通过 {@link SqlValidationService} 验证后交由 {@link SqlPermissionService} 改写，
 * 最终由调用方决定执行方式（预览 / 执行）。
 *
 * <p>与 {@link Nl2SqlService} 的区别：
 * {@link Nl2SqlService} 是 NL2SQL 入口（自然语言 → SQL），侧重自然语言解析；
 * 本类是语义编译入口（结构化意图 → SQL），侧重白名单编译与安全校验。
 *
 * @see AllowedSqlObjects
 * @see SqlValidationService
 * @see SqlPermissionService
 */
@Service
public class SemanticQueryCompiler {

    private static final Logger log = LoggerFactory.getLogger(SemanticQueryCompiler.class);

    private static final Pattern AGGREGATION_PATTERN = Pattern.compile(
            "(sum|count|avg|max|min)\\s*\\(\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );

    private final AllowedSqlObjects allowedSqlObjects;
    private final SqlValidationService sqlValidationService;
    private final SqlPermissionService sqlPermissionService;

    public SemanticQueryCompiler(AllowedSqlObjects allowedSqlObjects,
                                SqlValidationService sqlValidationService,
                                SqlPermissionService sqlPermissionService) {
        this.allowedSqlObjects = allowedSqlObjects;
        this.sqlValidationService = sqlValidationService;
        this.sqlPermissionService = sqlPermissionService;
    }

    /**
     * 从结构化编译参数编译受控 SQL。
     *
     * <p>编译流程：
     * <ol>
     *   <li>校验表名是否在白名单</li>
     *   <li>校验所有维度列是否在白名单</li>
     *   <li>校验聚合表达式中的函数和列名</li>
     *   <li>追加区域权限过滤条件</li>
     *   <li>交由 {@link SqlValidationService} 验证</li>
     *   <li>交由 {@link SqlPermissionService} 改写</li>
     * </ol>
     *
     * @param request 编译请求（表名、指标列、维度、时间范围、区域）
     * @return 编译结果（候选 SQL → 验证后 SQL → 权限改写后 SQL）
     * @throws IllegalArgumentException 当表名或维度不在白名单
     */
    public CompiledQuery compile(CompileRequest request) {
        validateTableName(request.tableName());
        List<String> validatedDimensions = validateDimensions(request.dimensions());
        validateAggregation(request.aggregationColumn(), request.aggregationFunction());

        String candidateSql = buildCandidateSql(
                request.tableName(),
                request.aggregationFunction(),
                request.aggregationColumn(),
                validatedDimensions,
                request.dateColumn(),
                request.startDate(),
                request.endDate(),
                null
        );
        List<String> warnings = new ArrayList<>();

        try {
            ValidatedSql validatedSql = sqlValidationService.validate(candidateSql);
            PermissionRewrittenSql rewrittenSql = sqlPermissionService.rewrite(validatedSql);
            log.info("compiled SQL for table={}, dimensions={}, finalSql={}", request.tableName(), validatedDimensions, rewrittenSql.sql());
            return new CompiledQuery(
                    request.tableName(),
                    request.aggregationColumn(),
                    validatedDimensions,
                    candidateSql,
                    validatedSql.sql(),
                    rewrittenSql.sql(),
                    rewrittenSql.summary(),
                    warnings
            );
        } catch (Exception ex) {
            log.warn("compilation failed for table={}: {}", request.tableName(), ex.getMessage());
            warnings.add("SQL 编译未通过白名单校验：" + ex.getMessage());
            return new CompiledQuery(
                    request.tableName(),
                    request.aggregationColumn(),
                    validatedDimensions,
                    candidateSql,
                    null,
                    null,
                    null,
                    warnings
            );
        }
    }

    /**
     * 编译单指标查询请求（含用户区域上下文）。
     *
     * @param tableName         表名
     * @param aggregationFunc   聚合函数（count/sum/avg/max/min）
     * @param aggregationColumn 聚合列
     * @param dimensions        维度列列表（group by）
     * @param dateColumn        时间列
     * @param startDate         开始日期（yyyy-MM-dd）
     * @param endDate           结束日期（yyyy-MM-dd）
     * @param regionCode        区域代码（从用户上下文自动追加到 WHERE）
     * @return 编译结果
     */
    public CompiledQuery compileSingleMetric(String tableName,
                                              String aggregationFunc,
                                              String aggregationColumn,
                                              List<String> dimensions,
                                              String dateColumn,
                                              String startDate,
                                              String endDate,
                                              String regionCode) {
        validateTableName(tableName);
        List<String> validatedDimensions = validateDimensions(dimensions);
        validateAggregation(aggregationColumn, aggregationFunc);

        String candidateSql = buildCandidateSql(
                tableName,
                aggregationFunc,
                aggregationColumn,
                validatedDimensions,
                dateColumn,
                startDate,
                endDate,
                regionCode
        );

        try {
            ValidatedSql validatedSql = sqlValidationService.validate(candidateSql);
            PermissionRewrittenSql rewrittenSql = sqlPermissionService.rewrite(validatedSql);
            return new CompiledQuery(
                    tableName,
                    aggregationColumn,
                    validatedDimensions,
                    candidateSql,
                    validatedSql.sql(),
                    rewrittenSql.sql(),
                    rewrittenSql.summary(),
                    List.of()
            );
        } catch (Exception ex) {
            log.warn("compileSingleMetric failed for table={}: {}", tableName, ex.getMessage());
            return new CompiledQuery(
                    tableName,
                    aggregationColumn,
                    validatedDimensions,
                    candidateSql,
                    null,
                    null,
                    "编译失败: " + ex.getMessage(),
                    List.of("编译失败: " + ex.getMessage())
            );
        }
    }

    /**
     * 快速校验表名白名单。
     *
     * @param tableName 待校验表名
     * @return true 在白名单中，false 不在
     */
    public boolean isTableAllowed(String tableName) {
        return allowedSqlObjects.isTableAllowed(tableName);
    }

    /**
     * 快速校验维度列白名单。
     *
     * @param dimension 待校验维度
     * @return true 在白名单中，false 不在
     */
    public boolean isDimensionAllowed(String dimension) {
        return allowedSqlObjects.isDimensionAllowed(dimension);
    }

    /**
     * 快速校验聚合函数白名单。
     *
     * @param function 待校验函数名
     * @return true 在白名单中，false 不在
     */
    public boolean isFunctionAllowed(String function) {
        return allowedSqlObjects.isFunctionAllowed(function);
    }

    // --- 内部方法 ---

    private void validateTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        if (!allowedSqlObjects.isTableAllowed(tableName)) {
            throw new IllegalArgumentException("表名 [" + tableName + "] 不在白名单允许范围内");
        }
    }

    private List<String> validateDimensions(List<String> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return List.of();
        }
        List<String> validated = new ArrayList<>();
        for (String dim : dimensions) {
            if (dim == null || dim.isBlank()) {
                continue;
            }
            String normalized = dim.trim().toLowerCase(Locale.ROOT);
            if (!allowedSqlObjects.isDimensionAllowed(normalized)) {
                log.debug("dimension [{}] not in whitelist, skipping", dim);
                continue;
            }
            validated.add(normalized);
        }
        return validated;
    }

    private void validateAggregation(String column, String function) {
        if (function == null || function.isBlank()) {
            throw new IllegalArgumentException("聚合函数不能为空");
        }
        if (!allowedSqlObjects.isFunctionAllowed(function)) {
            throw new IllegalArgumentException("聚合函数 [" + function + "] 不在白名单允许范围内");
        }
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("聚合列不能为空");
        }
    }

    private String buildCandidateSql(String tableName,
                                      String aggregationFunc,
                                      String aggregationColumn,
                                      List<String> dimensions,
                                      String dateColumn,
                                      String startDate,
                                      String endDate,
                                      String regionCode) {
        StringBuilder sql = new StringBuilder("select ");
        if (!dimensions.isEmpty()) {
            sql.append(String.join(", ", dimensions)).append(", ");
        }
        sql.append(aggregationFunc).append("(").append(aggregationColumn).append(") as metric_value")
                .append(" from ").append(tableName)
                .append(" where ").append(dateColumn)
                .append(" between '").append(startDate).append("' and '").append(endDate).append("'");

        String resolvedRegion = regionCode != null && !regionCode.isBlank() ? regionCode : null;
        if (resolvedRegion == null && UserContextHolder.get() != null) {
            resolvedRegion = UserContextHolder.get().region();
        }
        if (resolvedRegion != null && !resolvedRegion.isBlank()) {
            sql.append(" and region_code = '").append(resolvedRegion).append("'");
        }

        if (!dimensions.isEmpty()) {
            sql.append(" group by ").append(String.join(", ", dimensions));
            sql.append(" order by metric_value desc");
        }
        sql.append(" limit 200");
        return sql.toString();
    }

    // --- 内部类 ---

    /**
     * 编译请求。
     *
     * @param tableName          表名（白名单校验）
     * @param aggregationFunction 聚合函数
     * @param aggregationColumn   聚合列
     * @param dimensions          维度列列表（group by）
     * @param dateColumn          时间列
     * @param startDate           开始日期
     * @param endDate             结束日期
     */
    public record CompileRequest(
            String tableName,
            String aggregationFunction,
            String aggregationColumn,
            List<String> dimensions,
            String dateColumn,
            String startDate,
            String endDate
    ) {}

    /**
     * 编译结果。
     *
     * @param tableName           源表名
     * @param aggregationColumn  聚合列
     * @param dimensions          验证通过后的维度列
     * @param candidateSql        编译生成的候选 SQL（未经验证）
     * @param validatedSql        白名单验证通过后的 SQL（{@code null} 表示验证失败）
     * @param rewrittenSql        权限改写后的 SQL（{@code null} 表示未通过）
     * @param rewriteSummary      权限改写说明
     * @param warnings             警告信息（维度被过滤、编译失败等）
     */
    public record CompiledQuery(
            String tableName,
            String aggregationColumn,
            List<String> dimensions,
            String candidateSql,
            String validatedSql,
            String rewrittenSql,
            String rewriteSummary,
            List<String> warnings
    ) {
        public boolean isSuccess() {
            return validatedSql != null && rewrittenSql != null;
        }
    }
}