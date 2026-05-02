package com.example.urbanagent.risk.controller;

import com.example.urbanagent.common.api.ApiResponse;
import com.example.urbanagent.risk.application.LegalReviewApplicationService;
import com.example.urbanagent.risk.application.dto.LegalReviewActionRequest;
import com.example.urbanagent.risk.application.dto.LegalReviewView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/legal-reviews")
@Tag(name = "Legal Reviews", description = "高风险回答法制审核接口")
public class LegalReviewController {

    private final LegalReviewApplicationService legalReviewApplicationService;

    public LegalReviewController(LegalReviewApplicationService legalReviewApplicationService) {
        this.legalReviewApplicationService = legalReviewApplicationService;
    }

    @GetMapping
    @Operation(summary = "查询审核列表", description = "按审核状态查询法制审核记录")
    public ApiResponse<List<LegalReviewView>> list(@Parameter(description = "审核状态") @RequestParam(required = false) String status) {
        return ApiResponse.success(legalReviewApplicationService.list(status));
    }

    @GetMapping("/{reviewId}")
    @Operation(summary = "查询审核详情", description = "查询单条法制审核记录")
    public ApiResponse<LegalReviewView> get(@Parameter(description = "审核 ID") @PathVariable String reviewId) {
        return ApiResponse.success(legalReviewApplicationService.get(reviewId));
    }

    @PostMapping("/{reviewId}/approve")
    @Operation(summary = "通过审核", description = "将法制审核记录标记为通过")
    public ApiResponse<LegalReviewView> approve(@Parameter(description = "审核 ID") @PathVariable String reviewId,
                                                @RequestBody(required = false) LegalReviewActionRequest request) {
        return ApiResponse.success(legalReviewApplicationService.approve(reviewId, emptySafe(request)));
    }

    @PostMapping("/{reviewId}/reject")
    @Operation(summary = "驳回审核", description = "驳回高风险回答并记录审核意见")
    public ApiResponse<LegalReviewView> reject(@Parameter(description = "审核 ID") @PathVariable String reviewId,
                                               @RequestBody(required = false) LegalReviewActionRequest request) {
        return ApiResponse.success(legalReviewApplicationService.reject(reviewId, emptySafe(request)));
    }

    @PostMapping("/{reviewId}/revise")
    @Operation(summary = "修订审核", description = "提交修订后的审核回答")
    public ApiResponse<LegalReviewView> revise(@Parameter(description = "审核 ID") @PathVariable String reviewId,
                                               @RequestBody(required = false) LegalReviewActionRequest request) {
        return ApiResponse.success(legalReviewApplicationService.revise(reviewId, emptySafe(request)));
    }

    @PostMapping("/{reviewId}/request-more-facts")
    @Operation(summary = "要求补充事实", description = "要求业务人员补充事实后再给出审核结论")
    public ApiResponse<LegalReviewView> requestMoreFacts(@Parameter(description = "审核 ID") @PathVariable String reviewId,
                                                         @RequestBody(required = false) LegalReviewActionRequest request) {
        return ApiResponse.success(legalReviewApplicationService.requestMoreFacts(reviewId, emptySafe(request)));
    }

    private LegalReviewActionRequest emptySafe(LegalReviewActionRequest request) {
        return request == null ? new LegalReviewActionRequest(null, null) : request;
    }
}
