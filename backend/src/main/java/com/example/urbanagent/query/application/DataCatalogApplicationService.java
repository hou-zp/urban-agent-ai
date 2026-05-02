package com.example.urbanagent.query.application;

import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.example.urbanagent.query.application.dto.DataCatalogSyncResult;
import com.example.urbanagent.query.application.dto.DataFieldView;
import com.example.urbanagent.query.application.dto.DataTableView;
import com.example.urbanagent.query.application.dto.MetricDefinitionView;
import com.example.urbanagent.query.domain.DataField;
import com.example.urbanagent.query.domain.DataSource;
import com.example.urbanagent.query.domain.DataSourceType;
import com.example.urbanagent.query.domain.DataTable;
import com.example.urbanagent.query.domain.MetricDefinition;
import com.example.urbanagent.query.domain.SensitiveLevel;
import com.example.urbanagent.query.repository.DataFieldRepository;
import com.example.urbanagent.query.repository.DataSourceRepository;
import com.example.urbanagent.query.repository.DataTableRepository;
import com.example.urbanagent.query.repository.MetricDefinitionRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class DataCatalogApplicationService {

    private final DataSourceRepository dataSourceRepository;
    private final DataTableRepository dataTableRepository;
    private final DataFieldRepository dataFieldRepository;
    private final MetricDefinitionRepository metricDefinitionRepository;
    private final QueryCatalogViewMapper viewMapper;
    private final JdbcTemplate jdbcTemplate;
    private final QueryAccessPolicyService queryAccessPolicyService;

    public DataCatalogApplicationService(DataSourceRepository dataSourceRepository,
                                         DataTableRepository dataTableRepository,
                                         DataFieldRepository dataFieldRepository,
                                         MetricDefinitionRepository metricDefinitionRepository,
                                         QueryCatalogViewMapper viewMapper,
                                         JdbcTemplate jdbcTemplate,
                                         QueryAccessPolicyService queryAccessPolicyService) {
        this.dataSourceRepository = dataSourceRepository;
        this.dataTableRepository = dataTableRepository;
        this.dataFieldRepository = dataFieldRepository;
        this.metricDefinitionRepository = metricDefinitionRepository;
        this.viewMapper = viewMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.queryAccessPolicyService = queryAccessPolicyService;
    }

    @Transactional
    public DataCatalogSyncResult syncDemoCatalog() {
        dataFieldRepository.deleteAllInBatch();
        metricDefinitionRepository.deleteAllInBatch();
        dataTableRepository.deleteAllInBatch();
        dataSourceRepository.deleteAllInBatch();

        DataSource opsSource = dataSourceRepository.save(new DataSource(
                "城管运营分析库",
                DataSourceType.POSTGRESQL,
                "urban-ops-readonly",
                true
        ));

        DataTable complaintTable = dataTableRepository.save(new DataTable(
                opsSource.getId(),
                "fact_complaint_order",
                "投诉工单事实表",
                "complaint:read",
                "city",
                true
        ));
        DataTable inspectionTable = dataTableRepository.save(new DataTable(
                opsSource.getId(),
                "fact_inspection_record",
                "巡查记录事实表",
                "inspection:read",
                "district-a",
                true
        ));
        DataTable caseTable = dataTableRepository.save(new DataTable(
                opsSource.getId(),
                "fact_case_handle",
                "案件办理事实表",
                "case:read",
                "city",
                true
        ));
        DataTable oilFumeTable = dataTableRepository.save(new DataTable(
                opsSource.getId(),
                "fact_oil_fume_warning",
                "柯桥区油烟浓度预警事实表",
                "oil-fume:read",
                "shaoxing-keqiao",
                true
        ));
        DataTable cateringUnitTable = dataTableRepository.save(new DataTable(
                opsSource.getId(),
                "dim_catering_unit",
                "餐饮单位基础表",
                "oil-fume:read",
                "shaoxing-keqiao",
                true
        ));
        DataTable oilFumeWarningEventTable = dataTableRepository.save(new DataTable(
                opsSource.getId(),
                "fact_oil_fume_warning_event",
                "油烟预警批次明细表",
                "oil-fume:read",
                "shaoxing-keqiao",
                true
        ));
        DataTable oilFumeClosureTable = dataTableRepository.save(new DataTable(
                opsSource.getId(),
                "fact_oil_fume_closure_record",
                "油烟预警闭环记录表",
                "oil-fume:read",
                "shaoxing-keqiao",
                true
        ));
        DataTable oilFumeInspectionTable = dataTableRepository.save(new DataTable(
                opsSource.getId(),
                "fact_oil_fume_device_inspection",
                "油烟设备巡检记录表",
                "oil-fume:read",
                "shaoxing-keqiao",
                true
        ));

        List<DataField> fields = List.of(
                new DataField(complaintTable.getId(), "report_date", "上报日期", "date", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(complaintTable.getId(), "street_code", "街道编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(complaintTable.getId(), "region_code", "区域编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(complaintTable.getId(), "complaint_count", "投诉数量", "integer", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(complaintTable.getId(), "reporter_phone", "投诉人电话", "varchar", SensitiveLevel.RESTRICTED, "ADMIN"),
                new DataField(inspectionTable.getId(), "inspection_date", "巡查日期", "date", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(inspectionTable.getId(), "grid_name", "网格名称", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(inspectionTable.getId(), "street_code", "街道编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(inspectionTable.getId(), "region_code", "区域编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(inspectionTable.getId(), "problem_count", "问题数量", "integer", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(caseTable.getId(), "case_date", "立案日期", "date", SensitiveLevel.PUBLIC, "ADMIN,MANAGER"),
                new DataField(caseTable.getId(), "case_status", "案件状态", "varchar", SensitiveLevel.PUBLIC, "ADMIN,MANAGER"),
                new DataField(caseTable.getId(), "street_code", "街道编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,MANAGER"),
                new DataField(caseTable.getId(), "region_code", "区域编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,MANAGER"),
                new DataField(caseTable.getId(), "overdue_flag", "是否超期", "boolean", SensitiveLevel.INTERNAL, "ADMIN,MANAGER"),
                new DataField(oilFumeTable.getId(), "warning_date", "预警日期", "date", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeTable.getId(), "street_code", "镇街编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeTable.getId(), "region_code", "区域编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeTable.getId(), "warning_level", "预警等级", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeTable.getId(), "warning_count", "油烟超标预警数量", "integer", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeTable.getId(), "unclosed_count", "未闭环预警数量", "integer", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeTable.getId(), "avg_concentration", "平均油烟浓度", "decimal", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeTable.getId(), "max_concentration", "最高油烟浓度", "decimal", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(cateringUnitTable.getId(), "unit_code", "单位编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(cateringUnitTable.getId(), "unit_name", "单位名称", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(cateringUnitTable.getId(), "street_code", "镇街编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(cateringUnitTable.getId(), "business_type", "业态类型", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(cateringUnitTable.getId(), "contact_name", "联系人", "varchar", SensitiveLevel.INTERNAL, "ADMIN,MANAGER"),
                new DataField(cateringUnitTable.getId(), "contact_phone", "联系电话", "varchar", SensitiveLevel.INTERNAL, "ADMIN,MANAGER"),
                new DataField(cateringUnitTable.getId(), "purifier_model", "净化设备型号", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(cateringUnitTable.getId(), "purifier_enabled", "净化设备启用状态", "boolean", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(cateringUnitTable.getId(), "online_status", "在线状态", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeWarningEventTable.getId(), "warning_batch_code", "预警批次编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeWarningEventTable.getId(), "unit_code", "单位编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeWarningEventTable.getId(), "unit_name", "单位名称", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeWarningEventTable.getId(), "street_code", "镇街编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeWarningEventTable.getId(), "region_code", "区域编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeWarningEventTable.getId(), "warning_date", "预警日期", "date", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeWarningEventTable.getId(), "warning_level", "预警等级", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeWarningEventTable.getId(), "warning_count", "预警次数", "integer", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeWarningEventTable.getId(), "unclosed_count", "未闭环次数", "integer", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeWarningEventTable.getId(), "avg_concentration", "平均浓度", "decimal", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeWarningEventTable.getId(), "latest_status", "最新状态", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeClosureTable.getId(), "warning_batch_code", "预警批次编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeClosureTable.getId(), "closed_count", "已闭环数量", "integer", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeClosureTable.getId(), "closure_status", "闭环状态", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeClosureTable.getId(), "disposed_by", "处置单位", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeInspectionTable.getId(), "unit_code", "单位编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeInspectionTable.getId(), "inspection_date", "巡检日期", "date", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeInspectionTable.getId(), "street_code", "镇街编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeInspectionTable.getId(), "region_code", "区域编码", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeInspectionTable.getId(), "inspector_name", "巡检人员", "varchar", SensitiveLevel.INTERNAL, "ADMIN,MANAGER"),
                new DataField(oilFumeInspectionTable.getId(), "purifier_running", "设备运行状态", "boolean", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeInspectionTable.getId(), "purifier_cleaned", "设备清洗状态", "boolean", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeInspectionTable.getId(), "online_status", "在线状态", "varchar", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeInspectionTable.getId(), "issue_found", "是否发现问题", "boolean", SensitiveLevel.PUBLIC, "ADMIN,OFFICER,MANAGER"),
                new DataField(oilFumeInspectionTable.getId(), "issue_note", "内部意见", "varchar", SensitiveLevel.INTERNAL, "ADMIN,MANAGER")
        );
        dataFieldRepository.saveAll(fields);

        List<MetricDefinition> metrics = List.of(
                metric("complaint_count", "投诉数量", "统计指定时间范围内投诉工单总量", "sum(complaint_count)", "report_date", "street_code,region_code", complaintTable.getTableName(), "city", "CITY-COMPLAINT-2026.01", "verified", "全市", "2026-01-31T00:00:00Z"),
                metric("inspection_problem_count", "巡查问题数量", "统计巡查发现问题总量", "sum(problem_count)", "inspection_date", "grid_name,street_code", inspectionTable.getTableName(), "district-a", "DISTRICT-INSPECTION-2026.01", "verified", "示范区 A", "2026-01-31T00:00:00Z"),
                metric("overdue_case_count", "超期案件数量", "统计案件办理超期数量", "sum(case when overdue_flag then 1 else 0 end)", "case_date", "case_status,street_code", caseTable.getTableName(), "city", "CITY-CASE-2026.01", "verified", "全市", "2026-01-31T00:00:00Z"),
                metric("oil_fume_warning_count", "油烟超标预警数量", "统计柯桥区餐饮油烟浓度超标预警总量", "sum(warning_count)", "warning_date", "street_code,warning_level", oilFumeTable.getTableName(), "shaoxing-keqiao", "KQ-OILFUME-WARN-2026.01", "sensor", "绍兴市柯桥区", "2026-01-31T00:00:00Z"),
                metric("oil_fume_unclosed_warning_count", "未闭环油烟预警数量", "统计柯桥区尚未闭环的油烟浓度超标预警数量", "sum(unclosed_count)", "warning_date", "street_code,warning_level", oilFumeTable.getTableName(), "shaoxing-keqiao", "KQ-OILFUME-UNCLOSED-2026.01", "sensor", "绍兴市柯桥区", "2026-01-31T00:00:00Z"),
                metric("oil_fume_avg_concentration", "油烟平均浓度", "统计柯桥区油烟监测平均浓度", "avg(avg_concentration)", "warning_date", "street_code,warning_level", oilFumeTable.getTableName(), "shaoxing-keqiao", "KQ-OILFUME-AVG-2026.01", "sensor", "绍兴市柯桥区", "2026-01-31T00:00:00Z"),
                metric("oil_fume_max_concentration", "油烟最高浓度", "统计柯桥区油烟监测最高浓度", "max(max_concentration)", "warning_date", "street_code,warning_level", oilFumeTable.getTableName(), "shaoxing-keqiao", "KQ-OILFUME-MAX-2026.01", "sensor", "绍兴市柯桥区", "2026-01-31T00:00:00Z"),
                metric("oil_fume_closure_rate", "油烟预警闭环率", "统计柯桥区油烟预警闭环率", "case when sum(warning_count) = 0 then 0 else round((sum(warning_count) - sum(unclosed_count)) * 100.0 / sum(warning_count), 2) end", "warning_date", "street_code,warning_level", oilFumeWarningEventTable.getTableName(), "shaoxing-keqiao", "KQ-OILFUME-CLOSURE-2026.01", "derived", "绍兴市柯桥区", "2026-01-31T00:00:00Z"),
                metric("oil_fume_repeat_warning_count", "单位反复预警次数", "统计单位在指定时间范围内触发的油烟预警批次数，用于识别反复预警单位", "count(*)", "warning_date", "unit_name,street_code,warning_level", oilFumeWarningEventTable.getTableName(), "shaoxing-keqiao", "KQ-OILFUME-REPEAT-2026.01", "derived", "绍兴市柯桥区", "2026-01-31T00:00:00Z")
        );
        metricDefinitionRepository.saveAll(metrics);
        seedDemoFactDataFromScript();

        return new DataCatalogSyncResult(1, 8, fields.size(), metrics.size());
    }

    @Transactional(readOnly = true)
    public List<MetricDefinitionView> listMetrics() {
        UserContext userContext = UserContextHolder.get();
        return metricDefinitionRepository.findByEnabledTrueOrderByMetricCodeAsc()
                .stream()
                .filter(metric -> queryAccessPolicyService.canAccessRegion(metric.getRegionCode(), userContext))
                .map(viewMapper::toView)
                .toList();
    }

    private MetricDefinition metric(String metricCode,
                                    String metricName,
                                    String description,
                                    String aggregationExpr,
                                    String defaultTimeField,
                                    String commonDimensions,
                                    String tableName,
                                    String regionCode,
                                    String caliberVersion,
                                    String dataQuality,
                                    String applicableRegion,
                                    String dataUpdatedAt) {
        return new MetricDefinition(
                metricCode,
                metricName,
                description,
                aggregationExpr,
                defaultTimeField,
                commonDimensions,
                tableName,
                regionCode,
                caliberVersion,
                dataQuality,
                applicableRegion,
                Instant.parse(dataUpdatedAt),
                true
        );
    }

    @Transactional(readOnly = true)
    public List<DataTableView> listAuthorizedTables(String keyword) {
        UserContext userContext = UserContextHolder.get();
        List<DataTable> authorizedTables = dataTableRepository.findByEnabledTrueOrderByBusinessNameAsc()
                .stream()
                .filter(table -> queryAccessPolicyService.canAccessRegion(table.getRegionCode(), userContext))
                .filter(table -> matchesKeyword(table, keyword))
                .toList();
        if (authorizedTables.isEmpty()) {
            return List.of();
        }

        Map<String, List<DataFieldView>> fieldViewsByTableId = new LinkedHashMap<>();
        for (DataField field : dataFieldRepository.findByTableIdIn(authorizedTables.stream().map(DataTable::getId).toList())) {
            if (!queryAccessPolicyService.canAccessField(field, userContext)) {
                continue;
            }
            fieldViewsByTableId.computeIfAbsent(field.getTableId(), key -> new ArrayList<>()).add(viewMapper.toView(field));
        }

        return authorizedTables.stream()
                .map(table -> viewMapper.toView(
                        table,
                        fieldViewsByTableId.getOrDefault(table.getId(), List.of())
                                .stream()
                                .sorted(Comparator.comparing(DataFieldView::fieldName))
                                .toList()
                ))
                .toList();
    }

    private boolean matchesKeyword(DataTable table, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        return table.getTableName().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                || table.getBusinessName().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                || table.getPermissionTag().toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private void seedDemoFactDataFromScript() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("demo/query-demo-data.sql"));
        populator.execute(Objects.requireNonNull(jdbcTemplate.getDataSource()));
    }
}
