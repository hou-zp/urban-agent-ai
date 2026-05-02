package com.example.urbanagent.query.application;

import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.example.urbanagent.query.application.dto.BusinessRecordFieldView;
import com.example.urbanagent.query.application.dto.BusinessRecordQueryRequest;
import com.example.urbanagent.query.application.dto.BusinessRecordQueryView;
import com.example.urbanagent.query.application.dto.BusinessRecordType;
import com.example.urbanagent.query.domain.DataField;
import com.example.urbanagent.query.domain.DataSource;
import com.example.urbanagent.query.domain.DataTable;
import com.example.urbanagent.query.repository.DataFieldRepository;
import com.example.urbanagent.query.repository.DataSourceRepository;
import com.example.urbanagent.query.repository.DataTableRepository;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class BusinessRecordQueryService {

    private final DataTableRepository dataTableRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DataFieldRepository dataFieldRepository;
    private final QueryAccessPolicyService queryAccessPolicyService;
    private final List<BusinessDataAdapter> businessDataAdapters;
    private final BusinessRecordMaskingService businessRecordMaskingService;

    public BusinessRecordQueryService(DataTableRepository dataTableRepository,
                                      DataSourceRepository dataSourceRepository,
                                      DataFieldRepository dataFieldRepository,
                                      QueryAccessPolicyService queryAccessPolicyService,
                                      List<BusinessDataAdapter> businessDataAdapters,
                                      BusinessRecordMaskingService businessRecordMaskingService) {
        this.dataTableRepository = dataTableRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.dataFieldRepository = dataFieldRepository;
        this.queryAccessPolicyService = queryAccessPolicyService;
        this.businessDataAdapters = businessDataAdapters;
        this.businessRecordMaskingService = businessRecordMaskingService;
    }

    public BusinessRecordQueryView query(BusinessRecordQueryRequest request) {
        if (request == null || request.recordType() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "业务明细查询缺少记录类型");
        }
        UserContext userContext = UserContextHolder.get();
        RecordSpec spec = resolveSpec(request.recordType());
        DataTable dataTable = dataTableRepository.findByTableNameAndEnabledTrue(spec.tableName())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "当前业务明细表未接入: " + spec.tableName()));
        DataSource dataSource = dataSourceRepository.findById(dataTable.getDataSourceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "业务明细数据源不存在"));
        if (!queryAccessPolicyService.canAccessRegion(resolveTargetRegion(request, userContext), userContext)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "当前角色无权访问目标区域明细");
        }

        List<DataField> tableFields = dataFieldRepository.findByTableIdIn(List.of(dataTable.getId()));
        List<DataField> visibleFields = resolveVisibleFields(spec, tableFields, userContext);
        if (visibleFields.isEmpty()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "当前角色无权查看该类业务明细字段");
        }

        QueryStatement statement = buildQueryStatement(spec, dataTable, visibleFields, request, userContext);
        BusinessDataAdapter adapter = resolveAdapter(dataSource);
        List<Map<String, Object>> rows = adapter.execute(new BusinessDataQueryRequest(
                dataSource,
                statement.sql(),
                statement.parameters(),
                null,
                userContext
        ));
        List<Map<String, Object>> maskedRows = rows.stream()
                .map(row -> businessRecordMaskingService.apply(userContext, row, visibleFields))
                .toList();
        Set<String> maskedFields = businessRecordMaskingService.maskedFields(userContext, visibleFields);
        return new BusinessRecordQueryView(
                "record-" + UUID.randomUUID(),
                request.recordType(),
                dataTable.getTableName(),
                dataTable.getBusinessName(),
                dataTable.getPermissionTag(),
                resolveTargetRegion(request, userContext),
                maskedFields.stream().sorted().toList(),
                visibleFields.stream()
                        .map(field -> new BusinessRecordFieldView(
                                field.getFieldName(),
                                field.getBusinessName(),
                                field.getDataType(),
                                field.getSensitiveLevel().name(),
                                maskedFields.contains(field.getFieldName())
                        ))
                        .toList(),
                maskedRows
        );
    }

    private RecordSpec resolveSpec(BusinessRecordType recordType) {
        return switch (recordType) {
            case CASE -> new RecordSpec(
                    "fact_case_handle",
                    "case_date",
                    "case_date desc",
                    List.of("case_date", "case_status", "street_name", "region_code", "overdue_flag"),
                    List.of("street_name", "case_status")
            );
            case MERCHANT -> new RecordSpec(
                    "dim_catering_unit",
                    null,
                    "unit_code asc",
                    List.of("unit_code", "unit_name", "street_name", "region_code", "business_type",
                            "contact_name", "contact_phone", "purifier_model", "purifier_enabled", "online_status"),
                    List.of("unit_code", "unit_name", "street_name", "business_type")
            );
            case POINT -> new RecordSpec(
                    "fact_oil_fume_device_inspection",
                    "inspection_date",
                    "inspection_date desc",
                    List.of("unit_code", "inspection_date", "street_name", "region_code", "inspector_name",
                            "purifier_running", "purifier_cleaned", "online_status", "issue_found", "issue_note"),
                    List.of("unit_code", "street_name", "inspector_name", "issue_note")
            );
            case WORK_ORDER -> new RecordSpec(
                    "fact_complaint_order",
                    "report_date",
                    "report_date desc",
                    List.of("report_date", "street_name", "region_code", "complaint_count", "reporter_phone"),
                    List.of("street_name", "reporter_phone")
            );
            case LAND_PLOT -> throw new BusinessException(ErrorCode.BAD_REQUEST, "当前演示数据尚未接入地块明细");
        };
    }

    private List<DataField> resolveVisibleFields(RecordSpec spec, List<DataField> tableFields, UserContext userContext) {
        Map<String, DataField> fieldMap = new LinkedHashMap<>();
        for (DataField field : tableFields) {
            fieldMap.put(normalize(field.getFieldName()), field);
        }
        List<DataField> visibleFields = new ArrayList<>();
        for (String fieldName : spec.selectFields()) {
            DataField field = fieldMap.get(normalize(fieldName));
            if (field != null && queryAccessPolicyService.canAccessField(field, userContext)) {
                visibleFields.add(field);
            }
        }
        return visibleFields;
    }

    private QueryStatement buildQueryStatement(RecordSpec spec,
                                               DataTable dataTable,
                                               List<DataField> visibleFields,
                                               BusinessRecordQueryRequest request,
                                               UserContext userContext) {
        StringBuilder sql = new StringBuilder("select ");
        sql.append(String.join(", ", visibleFields.stream().map(DataField::getFieldName).toList()));
        sql.append(" from ").append(dataTable.getTableName()).append(" where 1 = 1");
        List<Object> parameters = new ArrayList<>();

        String targetRegion = resolveTargetRegion(request, userContext);
        if (targetRegion != null && !targetRegion.isBlank() && hasField(visibleFields, "region_code")) {
            sql.append(" and region_code = ?");
            parameters.add(targetRegion);
        }
        if (request.streetName() != null && !request.streetName().isBlank() && hasField(visibleFields, "street_name")) {
            sql.append(" and street_name = ?");
            parameters.add(request.streetName().trim());
        }
        appendTimeRange(sql, parameters, spec, request.timeRange());
        appendKeyword(sql, parameters, spec, request.keyword());
        appendStatus(sql, parameters, request.recordType(), request.status());
        sql.append(" order by ").append(spec.orderBy());
        sql.append(" limit ?");
        parameters.add(resolveLimit(request.limit()));
        return new QueryStatement(sql.toString(), List.copyOf(parameters));
    }

    private void appendTimeRange(StringBuilder sql,
                                 List<Object> parameters,
                                 RecordSpec spec,
                                 String timeRange) {
        if (spec.timeField() == null || timeRange == null || timeRange.isBlank()) {
            return;
        }
        if (timeRange.contains("~")) {
            String[] parts = timeRange.split("~", 2);
            LocalDate start = LocalDate.parse(parts[0].trim());
            LocalDate end = LocalDate.parse(parts[1].trim());
            sql.append(" and ").append(spec.timeField()).append(" between ? and ?");
            parameters.add(Date.valueOf(start));
            parameters.add(Date.valueOf(end));
            return;
        }
        if (timeRange.matches("\\d{4}-\\d{2}")) {
            YearMonth yearMonth = YearMonth.parse(timeRange);
            sql.append(" and ").append(spec.timeField()).append(" between ? and ?");
            parameters.add(Date.valueOf(yearMonth.atDay(1)));
            parameters.add(Date.valueOf(yearMonth.atEndOfMonth()));
        }
    }

    private void appendKeyword(StringBuilder sql,
                               List<Object> parameters,
                               RecordSpec spec,
                               String keyword) {
        if (keyword == null || keyword.isBlank() || spec.keywordFields().isEmpty()) {
            return;
        }
        sql.append(" and (");
        for (int index = 0; index < spec.keywordFields().size(); index++) {
            if (index > 0) {
                sql.append(" or ");
            }
            sql.append(spec.keywordFields().get(index)).append(" like ?");
            parameters.add("%" + keyword.trim() + "%");
        }
        sql.append(")");
    }

    private void appendStatus(StringBuilder sql,
                              List<Object> parameters,
                              BusinessRecordType recordType,
                              String status) {
        if (status == null || status.isBlank()) {
            return;
        }
        String normalizedStatus = normalize(status);
        switch (recordType) {
            case CASE -> {
                if ("pending".equals(normalizedStatus)) {
                    sql.append(" and case_status = ?");
                    parameters.add("办理中");
                } else if ("resolved".equals(normalizedStatus)) {
                    sql.append(" and case_status = ?");
                    parameters.add("已办结");
                } else if ("abnormal".equals(normalizedStatus)) {
                    sql.append(" and overdue_flag = ?");
                    parameters.add(Boolean.TRUE);
                }
            }
            case MERCHANT -> {
                if ("abnormal".equals(normalizedStatus) || "offline".equals(normalizedStatus)) {
                    sql.append(" and online_status = ?");
                    parameters.add("OFFLINE");
                } else if ("resolved".equals(normalizedStatus) || "online".equals(normalizedStatus)) {
                    sql.append(" and online_status = ?");
                    parameters.add("ONLINE");
                }
            }
            case POINT -> {
                if ("abnormal".equals(normalizedStatus) || "pending".equals(normalizedStatus) || "over_limit".equals(normalizedStatus)) {
                    sql.append(" and issue_found = ?");
                    parameters.add(Boolean.TRUE);
                } else if ("resolved".equals(normalizedStatus)) {
                    sql.append(" and issue_found = ?");
                    parameters.add(Boolean.FALSE);
                }
            }
            case WORK_ORDER -> {
                if ("abnormal".equals(normalizedStatus)) {
                    sql.append(" and complaint_count >= ?");
                    parameters.add(10);
                }
            }
            case LAND_PLOT -> throw new BusinessException(ErrorCode.BAD_REQUEST, "当前演示数据尚未接入地块明细");
        }
    }

    private BusinessDataAdapter resolveAdapter(DataSource dataSource) {
        return businessDataAdapters.stream()
                .filter(adapter -> adapter.supports(dataSource))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "当前业务数据源缺少可用适配器"));
    }

    private String resolveTargetRegion(BusinessRecordQueryRequest request, UserContext userContext) {
        if (request.regionCode() != null && !request.regionCode().isBlank()) {
            return request.regionCode().trim();
        }
        if ("ADMIN".equalsIgnoreCase(userContext.role())) {
            return null;
        }
        return userContext.region();
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 20;
        }
        return Math.min(limit, 50);
    }

    private boolean hasField(List<DataField> fields, String fieldName) {
        return fields.stream().anyMatch(field -> normalize(field.getFieldName()).equals(normalize(fieldName)));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record RecordSpec(
            String tableName,
            String timeField,
            String orderBy,
            List<String> selectFields,
            List<String> keywordFields
    ) {
    }

    private record QueryStatement(String sql, List<Object> parameters) {
    }
}
