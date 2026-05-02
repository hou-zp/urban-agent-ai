package com.example.urbanagent.agent.tool;

import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.query.application.BusinessRecordQueryService;
import com.example.urbanagent.query.application.dto.BusinessRecordQueryRequest;
import com.example.urbanagent.query.application.dto.BusinessRecordType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class BusinessRecordAgentTools {

    private final BusinessRecordQueryService businessRecordQueryService;
    private final ObjectMapper objectMapper;

    public BusinessRecordAgentTools(BusinessRecordQueryService businessRecordQueryService,
                                    ObjectMapper objectMapper) {
        this.businessRecordQueryService = businessRecordQueryService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "business_record_query", description = "查询案件、商户、地块、点位或工单明细，结果会自动执行权限过滤和敏感字段脱敏")
    @org.springframework.ai.tool.annotation.Tool(name = "business_record_query", description = "查询案件、商户、地块、点位或工单明细，结果会自动执行权限过滤和敏感字段脱敏")
    public String businessRecordQuery(
            @ToolParam(name = "recordType", description = "明细类型，可选 CASE、MERCHANT、LAND_PLOT、POINT、WORK_ORDER")
            @org.springframework.ai.tool.annotation.ToolParam(description = "明细类型，可选 CASE、MERCHANT、LAND_PLOT、POINT、WORK_ORDER", required = true)
            String recordType,
            @ToolParam(name = "keyword", description = "关键字，可用于单位名称、街道、状态等模糊搜索")
            @org.springframework.ai.tool.annotation.ToolParam(description = "关键字，可用于单位名称、街道、状态等模糊搜索", required = false)
            String keyword,
            @ToolParam(name = "regionCode", description = "区域编码，未传时默认使用当前用户区域")
            @org.springframework.ai.tool.annotation.ToolParam(description = "区域编码，未传时默认使用当前用户区域", required = false)
            String regionCode,
            @ToolParam(name = "streetName", description = "街道名称，可选精确过滤")
            @org.springframework.ai.tool.annotation.ToolParam(description = "街道名称，可选精确过滤", required = false)
            String streetName,
            @ToolParam(name = "status", description = "状态过滤，如 abnormal、pending、resolved、offline、online")
            @org.springframework.ai.tool.annotation.ToolParam(description = "状态过滤，如 abnormal、pending、resolved、offline、online", required = false)
            String status,
            @ToolParam(name = "timeRange", description = "时间范围，可传 yyyy-MM 或 yyyy-MM-dd~yyyy-MM-dd")
            @org.springframework.ai.tool.annotation.ToolParam(description = "时间范围，可传 yyyy-MM 或 yyyy-MM-dd~yyyy-MM-dd", required = false)
            String timeRange,
            @ToolParam(name = "limit", description = "返回条数，默认 20，最大 50")
            @org.springframework.ai.tool.annotation.ToolParam(description = "返回条数，默认 20，最大 50", required = false)
            Integer limit) {
        BusinessRecordType resolvedRecordType = resolveRecordType(recordType);
        BusinessRecordQueryRequest request = new BusinessRecordQueryRequest(
                resolvedRecordType,
                keyword,
                regionCode,
                streetName,
                status,
                timeRange,
                limit
        );
        try {
            return objectMapper.writeValueAsString(businessRecordQueryService.query(request));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("业务明细结果序列化失败", ex);
        }
    }

    private BusinessRecordType resolveRecordType(String recordType) {
        if (recordType == null || recordType.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "业务明细查询缺少 recordType");
        }
        try {
            return BusinessRecordType.valueOf(recordType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的业务明细类型: " + recordType);
        }
    }
}
