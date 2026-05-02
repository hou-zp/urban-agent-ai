package com.example.urbanagent.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "urban-agent.knowledge.upload")
public class KnowledgeUploadSecurityProperties {

    private long maxFileSizeBytes = 1_048_576L;
    private int maxFilenameLength = 128;
    private List<String> allowedContentTypes = new ArrayList<>(List.of(
            "text/plain",
            "text/markdown"
    ));
    private List<String> allowedExtensions = new ArrayList<>(List.of(
            "txt",
            "md",
            "markdown"
    ));

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public int getMaxFilenameLength() {
        return maxFilenameLength;
    }

    public void setMaxFilenameLength(int maxFilenameLength) {
        this.maxFilenameLength = maxFilenameLength;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }
}
