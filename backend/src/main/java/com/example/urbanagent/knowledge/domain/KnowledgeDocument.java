package com.example.urbanagent.knowledge.domain;

import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocument {

    @Id
    private String id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private KnowledgeCategory category;

    @Column(length = 120)
    private String sourceOrg;

    @Column(length = 120)
    private String documentNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private KnowledgeSecurityLevel securityLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private KnowledgeDocumentStatus status;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Column(length = 64)
    private String regionCode;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(length = 255)
    private String attachmentRef;

    @Column(length = 255)
    private String attachmentFileName;

    @Column(length = 120)
    private String attachmentMimeType;

    private Long attachmentSizeBytes;

    @Column(length = 500)
    private String sourceUrl;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(length = 120)
    private String mimeType;

    @Column(nullable = false, length = 64)
    private String createUserId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant indexedAt;

    @Column(length = 500)
    private String failedReason;

    protected KnowledgeDocument() {
    }

    public KnowledgeDocument(String title,
                             KnowledgeCategory category,
                             String sourceOrg,
                             String documentNumber,
                             KnowledgeSecurityLevel securityLevel,
                             LocalDate effectiveFrom,
                             LocalDate effectiveTo,
                             String regionCode,
                             String summary,
                             String attachmentRef,
                             String sourceUrl,
                             String content,
                             String fileName,
                             String mimeType,
                             String createUserId) {
        this.title = title;
        this.category = category;
        this.sourceOrg = sourceOrg;
        this.documentNumber = documentNumber;
        this.securityLevel = securityLevel == null ? KnowledgeSecurityLevel.INTERNAL : securityLevel;
        this.status = KnowledgeDocumentStatus.DRAFT;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.regionCode = regionCode;
        this.summary = summary;
        this.attachmentRef = attachmentRef;
        this.sourceUrl = sourceUrl;
        this.content = content;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.createUserId = createUserId;
    }

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public void markIndexing() {
        status = KnowledgeDocumentStatus.INDEXING;
        failedReason = null;
    }

    public void markActive() {
        status = resolveLifecycleStatus();
        indexedAt = Instant.now();
        failedReason = null;
    }

    public void markFailed(String reason) {
        status = KnowledgeDocumentStatus.FAILED;
        failedReason = reason;
    }

    public void updateStatus(KnowledgeDocumentStatus newStatus) {
        if (newStatus == KnowledgeDocumentStatus.INDEXING || newStatus == KnowledgeDocumentStatus.FAILED) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_STATUS_INVALID);
        }
        status = newStatus == KnowledgeDocumentStatus.ACTIVE ? resolveLifecycleStatus() : newStatus;
        if (status != KnowledgeDocumentStatus.FAILED) {
            failedReason = null;
        }
    }

    public void updateAttachment(String attachmentRef, String attachmentFileName, String attachmentMimeType, long attachmentSizeBytes) {
        this.attachmentRef = attachmentRef;
        this.attachmentFileName = attachmentFileName;
        this.attachmentMimeType = attachmentMimeType;
        this.attachmentSizeBytes = attachmentSizeBytes;
    }

    private KnowledgeDocumentStatus resolveLifecycleStatus() {
        if (effectiveTo != null && effectiveTo.isBefore(LocalDate.now())) {
            return KnowledgeDocumentStatus.EXPIRED;
        }
        return KnowledgeDocumentStatus.ACTIVE;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public KnowledgeCategory getCategory() {
        return category;
    }

    public String getSourceOrg() {
        return sourceOrg;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public KnowledgeSecurityLevel getSecurityLevel() {
        return securityLevel;
    }

    public KnowledgeDocumentStatus getStatus() {
        return status;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public String getSummary() {
        return summary;
    }

    public String getAttachmentRef() {
        return attachmentRef;
    }

    public String getAttachmentFileName() {
        return attachmentFileName;
    }

    public String getAttachmentMimeType() {
        return attachmentMimeType;
    }

    public Long getAttachmentSizeBytes() {
        return attachmentSizeBytes;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getContent() {
        return content;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getCreateUserId() {
        return createUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getIndexedAt() {
        return indexedAt;
    }

    public String getFailedReason() {
        return failedReason;
    }
}
