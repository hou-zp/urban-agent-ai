package com.example.urbanagent.query.controller;

import com.example.urbanagent.common.api.ApiResponse;
import com.example.urbanagent.query.application.DataCatalogApplicationService;
import com.example.urbanagent.query.application.dto.DataCatalogSyncResult;
import com.example.urbanagent.query.application.dto.DataTableView;
import com.example.urbanagent.query.application.dto.MetricDefinitionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/data/catalog")
@Tag(name = "Data Catalog", description = "智能问数数据目录、指标和授权表接口")
public class DataCatalogController {

    private final DataCatalogApplicationService dataCatalogApplicationService;

    public DataCatalogController(DataCatalogApplicationService dataCatalogApplicationService) {
        this.dataCatalogApplicationService = dataCatalogApplicationService;
    }

    @PostMapping("/sync")
    @Operation(summary = "同步演示数据目录", description = "写入 MVP 演示用的数据源、表、字段和指标定义")
    public ApiResponse<DataCatalogSyncResult> syncCatalog() {
        return ApiResponse.success(dataCatalogApplicationService.syncDemoCatalog());
    }

    @GetMapping("/metrics")
    @Operation(summary = "查询指标定义", description = "查询当前可用的问数指标和统计口径")
    public ApiResponse<List<MetricDefinitionView>> listMetrics() {
        return ApiResponse.success(dataCatalogApplicationService.listMetrics());
    }

    @GetMapping("/tables")
    @Operation(summary = "查询授权数据表", description = "按当前用户角色和区域查询可访问的数据表")
    public ApiResponse<List<DataTableView>> listTables(@Parameter(description = "表名或业务名称关键字") @RequestParam(required = false) String keyword) {
        return ApiResponse.success(dataCatalogApplicationService.listAuthorizedTables(keyword));
    }
}
