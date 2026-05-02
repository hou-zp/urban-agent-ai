package com.example.urbanagent.query.controller;

import com.example.urbanagent.common.api.ApiResponse;
import com.example.urbanagent.query.application.BusinessRecordQueryService;
import com.example.urbanagent.query.application.QueryApplicationService;
import com.example.urbanagent.query.application.dto.BusinessRecordQueryRequest;
import com.example.urbanagent.query.application.dto.BusinessRecordQueryView;
import com.example.urbanagent.query.application.dto.QueryAnswerView;
import com.example.urbanagent.query.application.dto.ExecuteQueryRequest;
import com.example.urbanagent.query.application.dto.PreviewQueryRequest;
import com.example.urbanagent.query.application.dto.QueryExecuteView;
import com.example.urbanagent.query.application.dto.QueryPreviewView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data/query")
@Tag(name = "Data Query", description = "智能问数 SQL 预览和只读执行接口")
public class DataQueryController {

    private final QueryApplicationService queryApplicationService;
    private final BusinessRecordQueryService businessRecordQueryService;

    public DataQueryController(QueryApplicationService queryApplicationService,
                               BusinessRecordQueryService businessRecordQueryService) {
        this.queryApplicationService = queryApplicationService;
        this.businessRecordQueryService = businessRecordQueryService;
    }

    @PostMapping("/preview")
    @Operation(summary = "预览问数 SQL", description = "根据自然语言问题生成候选 SQL、口径说明和权限改写结果，不执行查询")
    public ApiResponse<QueryPreviewView> preview(@Valid @RequestBody PreviewQueryRequest request) {
        return ApiResponse.success(queryApplicationService.preview(request));
    }

    @PostMapping("/answer")
    @Operation(summary = "生成问数答复", description = "自动拆解自然语言问题、执行只读查询并合成可直接展示给用户的自然语言答复")
    public ApiResponse<QueryAnswerView> answer(@Valid @RequestBody PreviewQueryRequest request) {
        return ApiResponse.success(queryApplicationService.answer(request));
    }

    @PostMapping("/execute")
    @Operation(summary = "执行只读查询", description = "校验并执行经过授权改写的只读 SQL")
    public ApiResponse<QueryExecuteView> execute(@Valid @RequestBody ExecuteQueryRequest request) {
        return ApiResponse.success(queryApplicationService.execute(request));
    }

    @PostMapping("/records")
    @Operation(summary = "查询业务明细", description = "按案件、商户、点位或工单类型查询业务明细，结果已按后端权限与脱敏规则处理")
    public ApiResponse<BusinessRecordQueryView> records(@RequestBody BusinessRecordQueryRequest request) {
        return ApiResponse.success(businessRecordQueryService.query(request));
    }
}
