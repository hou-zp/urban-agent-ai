package com.example.urbanagent.knowledge.application;

import com.example.urbanagent.audit.application.AuditLogService;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.knowledge.application.dto.KnowledgeAttachmentView;
import com.example.urbanagent.knowledge.domain.KnowledgeDocument;
import com.example.urbanagent.knowledge.repository.KnowledgeDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeAttachmentService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeAttachmentSecurityService securityService;
    private final KnowledgeAttachmentObjectStorage objectStorage;
    private final AuditLogService auditLogService;

    public KnowledgeAttachmentService(KnowledgeDocumentRepository documentRepository,
                                      KnowledgeAttachmentSecurityService securityService,
                                      KnowledgeAttachmentObjectStorage objectStorage,
                                      AuditLogService auditLogService) {
        this.documentRepository = documentRepository;
        this.securityService = securityService;
        this.objectStorage = objectStorage;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public KnowledgeAttachmentView uploadAttachment(String documentId, MultipartFile file) {
        KnowledgeDocument document = getDocument(documentId);
        securityService.assertCanManage(document);
        byte[] bytes = securityService.validateUpload(file);
        String originalFileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "attachment.bin"
                : file.getOriginalFilename();
        String attachmentRef = objectStorage.store(documentId, originalFileName, bytes);
        String mimeType = file.getContentType() == null || file.getContentType().isBlank()
                ? "application/octet-stream"
                : file.getContentType();
        document.updateAttachment(attachmentRef, originalFileName, mimeType, bytes.length);
        KnowledgeDocument saved = documentRepository.save(document);
        auditLogService.recordKnowledgeAttachmentUploaded(saved);
        return toView(saved);
    }

    @Transactional
    public KnowledgeAttachmentPayload downloadAttachment(String documentId) {
        KnowledgeDocument document = getDocument(documentId);
        securityService.assertCanDownload(document);
        if (document.getAttachmentRef() == null || document.getAttachmentRef().isBlank()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_ATTACHMENT_NOT_FOUND, "文档还没有附件");
        }
        if (document.getAttachmentFileName() == null || document.getAttachmentMimeType() == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_ATTACHMENT_NOT_FOUND, "附件元数据缺失，暂不可下载");
        }
        byte[] bytes = objectStorage.read(document.getAttachmentRef());
        auditLogService.recordKnowledgeAttachmentDownloaded(document);
        return new KnowledgeAttachmentPayload(
                bytes,
                document.getAttachmentFileName(),
                document.getAttachmentMimeType(),
                document.getAttachmentSizeBytes() == null ? bytes.length : document.getAttachmentSizeBytes()
        );
    }

    private KnowledgeDocument getDocument(String documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND));
    }

    private KnowledgeAttachmentView toView(KnowledgeDocument document) {
        return new KnowledgeAttachmentView(
                document.getId(),
                document.getAttachmentRef(),
                document.getAttachmentFileName(),
                document.getAttachmentMimeType(),
                document.getAttachmentSizeBytes() == null ? 0L : document.getAttachmentSizeBytes()
        );
    }
}
