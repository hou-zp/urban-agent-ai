package com.example.urbanagent.knowledge.application;

import com.example.urbanagent.common.config.ObjectStorageProperties;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

@Component
public class LocalKnowledgeAttachmentObjectStorage implements KnowledgeAttachmentObjectStorage {

    private static final String REF_PREFIX = "object://";

    private final ObjectStorageProperties properties;

    public LocalKnowledgeAttachmentObjectStorage(ObjectStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String store(String documentId, String fileName, byte[] bytes) {
        String sanitizedFileName = sanitizeFileName(fileName);
        String objectKey = documentId + "/" + UUID.randomUUID() + "-" + sanitizedFileName;
        Path bucketPath = Paths.get(properties.getLocalBasePath(), properties.getBucket());
        Path objectPath = bucketPath.resolve(objectKey);
        try {
            Files.createDirectories(objectPath.getParent());
            Files.write(objectPath, bytes);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件存储失败");
        }
        return REF_PREFIX + properties.getBucket() + "/" + objectKey;
    }

    @Override
    public byte[] read(String attachmentRef) {
        if (attachmentRef == null || attachmentRef.isBlank() || !attachmentRef.startsWith(REF_PREFIX)) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_ATTACHMENT_NOT_FOUND, "附件不是系统托管对象");
        }
        String objectLocation = attachmentRef.substring(REF_PREFIX.length());
        Path objectPath = Paths.get(properties.getLocalBasePath(), objectLocation);
        if (!Files.exists(objectPath) || Files.isDirectory(objectPath)) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_ATTACHMENT_NOT_FOUND, "附件不存在或已被清理");
        }
        try {
            return Files.readAllBytes(objectPath);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_ATTACHMENT_NOT_FOUND, "附件读取失败");
        }
    }

    private String sanitizeFileName(String fileName) {
        String candidate = fileName == null ? "attachment.bin" : fileName.trim();
        if (candidate.isBlank()) {
            candidate = "attachment.bin";
        }
        String sanitized = candidate.replace("\\", "/");
        int lastSlash = sanitized.lastIndexOf('/');
        if (lastSlash >= 0) {
            sanitized = sanitized.substring(lastSlash + 1);
        }
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.isBlank()) {
            sanitized = "attachment.bin";
        }
        return sanitized.toLowerCase(Locale.ROOT);
    }
}
