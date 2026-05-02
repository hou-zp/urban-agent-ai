package com.example.urbanagent.knowledge.application.dto;

import com.example.urbanagent.knowledge.domain.KnowledgeCategory;
import com.example.urbanagent.knowledge.domain.KnowledgeSecurityLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public class UploadKnowledgeDocumentRequest {

    @NotBlank
    private String title;

    @NotNull
    private KnowledgeCategory category;

    private String sourceOrg;

    private String documentNumber;

    private KnowledgeSecurityLevel securityLevel;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate effectiveFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate effectiveTo;

    private String regionCode;

    private String summary;

    private String attachmentRef;

    private String sourceUrl;

    @NotNull
    private MultipartFile file;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public KnowledgeCategory getCategory() {
        return category;
    }

    public void setCategory(KnowledgeCategory category) {
        this.category = category;
    }

    public String getSourceOrg() {
        return sourceOrg;
    }

    public void setSourceOrg(String sourceOrg) {
        this.sourceOrg = sourceOrg;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public void setDocumentNumber(String documentNumber) {
        this.documentNumber = documentNumber;
    }

    public KnowledgeSecurityLevel getSecurityLevel() {
        return securityLevel;
    }

    public void setSecurityLevel(KnowledgeSecurityLevel securityLevel) {
        this.securityLevel = securityLevel;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getAttachmentRef() {
        return attachmentRef;
    }

    public void setAttachmentRef(String attachmentRef) {
        this.attachmentRef = attachmentRef;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }
}
