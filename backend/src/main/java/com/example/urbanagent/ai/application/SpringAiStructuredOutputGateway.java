package com.example.urbanagent.ai.application;

import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SpringAiStructuredOutputGateway implements StructuredOutputGateway {

    private static final String PROVIDER = "spring-ai-openai";
    private static final String SYSTEM_PROMPT = "你是城管业务智能助手，回答必须准确、简洁、可追溯。";
    private static final String MODEL_UNAVAILABLE_MESSAGE = "大模型服务暂不可用，请稍后再试。";
    private static final SpecVersion.VersionFlag DEFAULT_SCHEMA_VERSION = SpecVersion.VersionFlag.V202012;

    private final ChatClient chatClient;
    private final ModelProperties properties;
    private final ModelCallRecordService modelCallRecordService;
    private final ObjectMapper objectMapper;

    public SpringAiStructuredOutputGateway(ChatClient chatClient,
                                           ModelProperties properties,
                                           ModelCallRecordService modelCallRecordService,
                                           ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.modelCallRecordService = modelCallRecordService;
        this.objectMapper = objectMapper;
    }

    @Override
    public StructuredOutputResult generate(StructuredOutputRequest request) {
        ensureConfigured(request.jsonSchema());
        long startedAt = System.nanoTime();
        String auditPrompt = buildStructuredPromptForAudit(request.history(), request.prompt(), request.jsonSchema());
        try {
            int totalAttempts = request.maxRetries() + 1;
            String content = "";
            String validationError = "模型未返回结构化内容";
            int attempt;
            for (attempt = 1; attempt <= totalAttempts; attempt++) {
                content = chatClient.prompt(buildPrompt(request))
                        .call()
                        .content();
                validationError = validateStructuredOutput(content, request.jsonSchema());
                if (validationError == null) {
                    modelCallRecordService.recordSuccess(
                            PROVIDER,
                            modelName(),
                            "STRUCTURED_CHAT",
                            auditPrompt,
                            content,
                            elapsedMs(startedAt)
                    );
                    return new StructuredOutputResult(content, attempt, true, null);
                }
            }
            modelCallRecordService.recordSuccess(
                    PROVIDER,
                    modelName(),
                    "STRUCTURED_CHAT",
                    auditPrompt,
                    content,
                    elapsedMs(startedAt)
            );
            return new StructuredOutputResult(content, totalAttempts, false, validationError);
        } catch (RuntimeException ex) {
            BusinessException normalized = normalizeException(ex);
            modelCallRecordService.recordFailure(
                    PROVIDER,
                    modelName(),
                    "STRUCTURED_CHAT",
                    auditPrompt,
                    elapsedMs(startedAt),
                    normalized
            );
            throw normalized;
        }
    }

    private Prompt buildPrompt(StructuredOutputRequest request) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        for (String historyItem : request.history()) {
            if (historyItem != null && !historyItem.isBlank()) {
                messages.add(new UserMessage(historyItem));
            }
        }
        messages.add(new UserMessage(request.prompt()));
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelName())
                .temperature(properties.getTemperature())
                .responseFormat(buildResponseFormat(request.jsonSchema()))
                .build();
        return new Prompt(messages, options);
    }

    private ResponseFormat buildResponseFormat(String jsonSchema) {
        ResponseFormat.JsonSchema schema = ResponseFormat.JsonSchema.builder()
                .name("urban_agent_structured_output")
                .schema(jsonSchema)
                .strict(Boolean.TRUE)
                .build();
        return ResponseFormat.builder()
                .type(ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(schema)
                .build();
    }

    private String validateStructuredOutput(String content, String jsonSchema) {
        if (content == null || content.isBlank()) {
            return "模型未返回结构化内容";
        }
        try {
            JsonNode schemaNode = objectMapper.readTree(jsonSchema);
            JsonNode contentNode = objectMapper.readTree(content);
            JsonSchema compiledSchema = schemaFactory(schemaNode).getSchema(schemaNode);
            Set<ValidationMessage> errors = compiledSchema.validate(contentNode);
            if (errors.isEmpty()) {
                return null;
            }
            return errors.stream()
                    .map(ValidationMessage::getMessage)
                    .filter(message -> message != null && !message.isBlank())
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.joining("; "));
        } catch (JsonProcessingException ex) {
            return "模型未返回合法 JSON";
        } catch (RuntimeException ex) {
            return "结构化输出 schema 校验失败";
        }
    }

    private JsonSchemaFactory schemaFactory(JsonNode schemaNode) {
        SpecVersion.VersionFlag version = DEFAULT_SCHEMA_VERSION;
        JsonNode schemaId = schemaNode.get("$schema");
        if (schemaId != null && schemaId.isTextual()) {
            version = SpecVersion.VersionFlag.fromId(schemaId.asText()).orElse(DEFAULT_SCHEMA_VERSION);
        }
        return JsonSchemaFactory.getInstance(version);
    }

    private void ensureConfigured(String jsonSchema) {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "模型服务地址未配置");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "模型 API Key 未配置");
        }
        if (properties.getChatModel() == null || properties.getChatModel().isBlank()) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "chat model 未配置");
        }
        if (jsonSchema == null || jsonSchema.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结构化输出 JSON Schema 不能为空");
        }
    }

    private String modelName() {
        String chatModel = properties.getChatModel();
        return chatModel == null || chatModel.isBlank() ? "unknown" : chatModel.trim();
    }

    private String buildStructuredPromptForAudit(List<String> history, String prompt, String jsonSchema) {
        String basePrompt;
        if (history == null || history.isEmpty()) {
            basePrompt = prompt;
        } else {
            basePrompt = String.join("\n", history) + "\n" + prompt;
        }
        return basePrompt + "\nJSON_SCHEMA:\n" + jsonSchema;
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
