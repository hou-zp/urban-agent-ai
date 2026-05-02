package com.example.urbanagent.knowledge.application;

import com.example.urbanagent.common.config.ObjectStorageProperties;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.example.urbanagent.knowledge.domain.KnowledgeDocument;
import com.example.urbanagent.knowledge.domain.KnowledgeSecurityLevel;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

@Service
public class KnowledgeAttachmentSecurityService {

    private final ObjectStorageProperties properties;

    public KnowledgeAttachmentSecurityService(ObjectStorageProperties properties) {
        this.properties = properties;
    }

    public byte[] validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_ATTACHMENT_REJECTED, "附件不能为空");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_ATTACHMENT_REJECTED, "附件大小超出限制");
        }
        String contentType = normalize(file.getContentType());
        List<String> allowedContentTypes = properties.getAllowedContentTypes().stream().map(this::normalize).toList();
        if (!contentType.isBlank() && !allowedContentTypes.contains(contentType)) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_ATTACHMENT_REJECTED, "附件类型不受支持");
        }
        String extension = extractExtension(file.getOriginalFilename());
        List<String> allowedExtensions = properties.getAllowedExtensions().stream().map(this::normalize).toList();
        if (!extension.isBlank() && !allowedExtensions.contains(extension)) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_ATTACHMENT_REJECTED, "附件扩展名不受支持");
        }
        try {
            return file.getBytes();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_ATTACHMENT_REJECTED, "附件读取失败");
        }
    }

    public void assertCanManage(KnowledgeDocument document) {
        UserContext userContext = UserContextHolder.get();
        if ("ADMIN".equalsIgnoreCase(userContext.role())) {
            return;
        }
        if (!matchesRegion(document, userContext)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "当前区域无权管理该附件");
        }
    }

    public void assertCanDownload(KnowledgeDocument document) {
        UserContext userContext = UserContextHolder.get();
        if ("ADMIN".equalsIgnoreCase(userContext.role())) {
            return;
        }
        if (!matchesRegion(document, userContext)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "当前区域无权下载该附件");
        }
        if (document.getSecurityLevel() == KnowledgeSecurityLevel.CONFIDENTIAL) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "涉密附件仅管理员可下载");
        }
        if (document.getSecurityLevel() == KnowledgeSecurityLevel.INTERNAL
                && !"MANAGER".equalsIgnoreCase(userContext.role())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "内部附件仅管理员或主管可下载");
        }
    }

    private boolean matchesRegion(KnowledgeDocument document, UserContext userContext) {
        String documentRegion = normalize(document.getRegionCode());
        if (documentRegion.isBlank() || "city".equals(documentRegion)) {
            return true;
        }
        String userRegion = normalize(userContext.region());
        if (userRegion.isBlank() || "city".equals(userRegion)) {
            return true;
        }
        return documentRegion.equals(userRegion);
    }

    private String extractExtension(String fileName) {
        String normalized = fileName == null ? "" : fileName.trim();
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == normalized.length() - 1) {
            return "";
        }
        return normalize(normalized.substring(dotIndex + 1));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
