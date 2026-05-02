package com.example.urbanagent.ai.application;

import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class SpringAiChatModelGateway implements ChatModelGateway {

    private static final String PROVIDER = "spring-ai-openai";
    private static final String SYSTEM_PROMPT = "你是城管业务智能助手，回答必须准确、简洁、可追溯。";
    private static final String MODEL_UNAVAILABLE_MESSAGE = "大模型服务暂不可用，请稍后再试。";

    private final ChatClient chatClient;
    private final ModelProperties properties;
    private final ModelCallRecordService modelCallRecordService;

    public SpringAiChatModelGateway(ChatClient chatClient,
                                    ModelProperties properties,
                                    ModelCallRecordService modelCallRecordService) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.modelCallRecordService = modelCallRecordService;
    }

    @Override
    public String name() {
        return PROVIDER + ":" + modelName();
    }

    @Override
    public String modelName() {
        String chatModel = properties.getChatModel();
        return chatModel == null || chatModel.isBlank() ? "unknown" : chatModel.trim();
    }

    @Override
    public String chat(List<String> history, String prompt) {
        ensureConfigured();
        long startedAt = System.nanoTime();
        String auditPrompt = buildPromptForAudit(history, prompt);
        try {
            String response = chatClient.prompt(buildPrompt(history, prompt))
                    .call()
                    .content();
            modelCallRecordService.recordSuccess(
                    PROVIDER,
                    modelName(),
                    "CHAT",
                    auditPrompt,
                    response,
                    elapsedMs(startedAt)
            );
            return response;
        } catch (RuntimeException ex) {
            BusinessException normalized = normalizeException(ex);
            modelCallRecordService.recordFailure(
                    PROVIDER,
                    modelName(),
                    "CHAT",
                    auditPrompt,
                    elapsedMs(startedAt),
                    normalized
            );
            throw normalized;
        }
    }

    @Override
    public Stream<ModelChunk> streamChat(List<String> history, String prompt) {
        String response = chat(history, prompt);
        String[] tokens = response.split(" ");
        ArrayList<ModelChunk> chunks = new ArrayList<>();
        for (int index = 0; index < tokens.length; index++) {
            chunks.add(new ModelChunk(tokens[index] + (index == tokens.length - 1 ? "" : " "), false));
        }
        chunks.add(new ModelChunk("", true));
        return chunks.stream();
    }

    private Prompt buildPrompt(List<String> history, String prompt) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        if (history != null) {
            for (String item : history) {
                if (item != null && !item.isBlank()) {
                    messages.add(new UserMessage(item));
                }
            }
        }
        messages.add(new UserMessage(prompt));
        return new Prompt(messages);
    }

    private String buildPromptForAudit(List<String> history, String prompt) {
        if (history == null || history.isEmpty()) {
            return prompt;
        }
        return String.join("\n", history) + "\n" + prompt;
    }

    private void ensureConfigured() {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "模型服务地址未配置");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "模型 API Key 未配置");
        }
        if (properties.getChatModel() == null || properties.getChatModel().isBlank()) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "chat model 未配置");
        }
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private BusinessException normalizeException(RuntimeException ex) {
        if (ex instanceof BusinessException businessException) {
            if (businessException.errorCode() == ErrorCode.MODEL_UNAVAILABLE) {
                return new BusinessException(ErrorCode.MODEL_UNAVAILABLE, MODEL_UNAVAILABLE_MESSAGE);
            }
            return businessException;
        }
        return new BusinessException(ErrorCode.MODEL_UNAVAILABLE, MODEL_UNAVAILABLE_MESSAGE);
    }
}
