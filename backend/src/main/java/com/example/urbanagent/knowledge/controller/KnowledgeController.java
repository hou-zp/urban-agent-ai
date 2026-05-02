package com.example.urbanagent.knowledge.controller;

import com.example.urbanagent.common.api.ApiResponse;
import com.example.urbanagent.knowledge.application.KnowledgeAttachmentPayload;
import com.example.urbanagent.knowledge.application.KnowledgeAttachmentService;
import com.example.urbanagent.knowledge.application.KnowledgeApplicationService;
import com.example.urbanagent.knowledge.application.dto.KnowledgeAttachmentView;
import com.example.urbanagent.knowledge.application.dto.KnowledgeDocumentView;
import com.example.urbanagent.knowledge.application.dto.KnowledgeSearchHitView;
import com.example.urbanagent.knowledge.application.dto.UpdateKnowledgeStatusRequest;
import com.example.urbanagent.knowledge.application.dto.UploadKnowledgeDocumentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "Knowledge", description = "政策法规和业务知识文档管理接口")
public class KnowledgeController {

    private final KnowledgeApplicationService knowledgeService;
    private final KnowledgeAttachmentService knowledgeAttachmentService;

    public KnowledgeController(KnowledgeApplicationService knowledgeService,
                               KnowledgeAttachmentService knowledgeAttachmentService) {
        this.knowledgeService = knowledgeService;
        this.knowledgeAttachmentService = knowledgeAttachmentService;
    }

    @PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传知识文档", description = "上传文本类政策、法规或业务文档并保存元数据")
    public ApiResponse<KnowledgeDocumentView> uploadDocument(@Valid @ModelAttribute UploadKnowledgeDocumentRequest request) {
        return ApiResponse.success(knowledgeService.uploadDocument(request));
    }

    @GetMapping("/documents")
    @Operation(summary = "查询知识文档", description = "查询知识库文档列表和状态")
    public ApiResponse<List<KnowledgeDocumentView>> listDocuments() {
        return ApiResponse.success(knowledgeService.listDocuments());
    }

    @PostMapping("/documents/{documentId}/index")
    @Operation(summary = "索引知识文档", description = "解析文档、生成分块和 embedding，并激活可检索状态")
    public ApiResponse<KnowledgeDocumentView> indexDocument(@Parameter(description = "文档 ID") @PathVariable String documentId) {
        return ApiResponse.success(knowledgeService.indexDocument(documentId));
    }

    @PostMapping("/documents/{documentId}/status")
    @Operation(summary = "更新文档状态", description = "调整文档为 active、expired、abolished 等状态")
    public ApiResponse<KnowledgeDocumentView> updateStatus(@Parameter(description = "文档 ID") @PathVariable String documentId,
                                                           @Valid @RequestBody UpdateKnowledgeStatusRequest request) {
        return ApiResponse.success(knowledgeService.updateStatus(documentId, request));
    }

    @PostMapping(path = "/documents/{documentId}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文档附件", description = "将政策原文或补充附件写入对象存储，并绑定到知识文档")
    public ApiResponse<KnowledgeAttachmentView> uploadAttachment(@Parameter(description = "文档 ID") @PathVariable String documentId,
                                                                 @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(knowledgeAttachmentService.uploadAttachment(documentId, file));
    }

    @GetMapping("/documents/{documentId}/attachment")
    @Operation(summary = "下载文档附件", description = "按知识密级和区域权限下载已绑定的对象存储附件")
    public ResponseEntity<byte[]> downloadAttachment(@Parameter(description = "文档 ID") @PathVariable String documentId) {
        KnowledgeAttachmentPayload payload = knowledgeAttachmentService.downloadAttachment(documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.mimeType()))
                .header("Content-Disposition", "attachment; filename=\"" + payload.fileName() + "\"")
                .body(payload.bytes());
    }

    @GetMapping("/search")
    @Operation(summary = "检索知识片段", description = "按问题和分类召回可引用的知识分块")
    public ApiResponse<List<KnowledgeSearchHitView>> search(@Parameter(description = "检索问题") @RequestParam String query,
                                                            @Parameter(description = "知识分类") @RequestParam(required = false) String category,
                                                            @Parameter(description = "返回条数") @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.success(knowledgeService.search(query, category, limit));
    }
}
