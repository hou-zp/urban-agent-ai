package com.example.urbanagent.ai.application;

import com.example.urbanagent.ai.domain.ModelCallRecord;
import com.example.urbanagent.ai.repository.ModelCallRecordRepository;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.iam.domain.UserContextHolder;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelCallRecordService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 500;

    private final ModelCallRecordRepository modelCallRecordRepository;

    public ModelCallRecordService(ModelCallRecordRepository modelCallRecordRepository) {
        this.modelCallRecordRepository = modelCallRecordRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String provider,
                              String modelName,
                              String operation,
                              String prompt,
                              String completion,
                              long latencyMs) {
        modelCallRecordRepository.save(new ModelCallRecord(
                MDC.get("runId"),
                UserContextHolder.get().userId(),
                provider,
                modelName,
                operation,
                "SUCCESS",
                estimateTokens(prompt),
                estimateTokens(completion),
                latencyMs,
                null,
                null
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String provider,
                              String modelName,
                              String operation,
                              String prompt,
                              long latencyMs,
                              RuntimeException exception) {
        String errorCode = exception instanceof BusinessException businessException
                ? businessException.errorCode().name()
                : exception.getClass().getSimpleName();
        modelCallRecordRepository.save(new ModelCallRecord(
                MDC.get("runId"),
                UserContextHolder.get().userId(),
                provider,
                modelName,
                operation,
                "FAILED",
                estimateTokens(prompt),
                0,
                latencyMs,
                errorCode,
                truncate(exception.getMessage())
        ));
    }

    private int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        int codePoints = content.codePointCount(0, content.length());
        return Math.max(1, (codePoints + 3) / 4);
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() <= ERROR_MESSAGE_MAX_LENGTH
                ? message
                : message.substring(0, ERROR_MESSAGE_MAX_LENGTH);
    }
}
