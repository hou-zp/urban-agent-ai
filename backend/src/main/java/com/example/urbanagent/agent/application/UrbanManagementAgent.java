package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.MessageCitationView;
import com.example.urbanagent.agent.application.dto.ParsedQuestion;
import com.example.urbanagent.agent.tool.AgentExecutionContextHolder;
import com.example.urbanagent.agent.tool.UrbanToolRegistry;
import com.example.urbanagent.ai.application.ChatModelGateway;
import com.example.urbanagent.ai.config.AgentScopeConfig.AgentScopeModelFactory;
import com.example.urbanagent.ai.config.AgentScopeProperties;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.knowledge.application.KnowledgeSearchService;
import com.example.urbanagent.knowledge.application.dto.KnowledgeSearchHit;
import com.example.urbanagent.knowledge.domain.KnowledgeCategory;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.OpenAIChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class UrbanManagementAgent {

    private static final Logger log = LoggerFactory.getLogger(UrbanManagementAgent.class);
    private static final String MISSING_CITABLE_SOURCE_MESSAGE = "未检索到可引用的政策法规或业务依据，暂不能给出正式答复。请补充相关知识文档、缩小问题范围，或转人工核实后再使用。";
    private static final String MODEL_UNAVAILABLE_MESSAGE = "大模型服务暂不可用，请稍后再试。";

    private final AgentScopeModelFactory agentScopeModelFactory;
    private final AgentScopeProperties agentScopeProperties;
    private final UrbanToolRegistry toolRegistry;
    private final AgentToolAuditHook toolAuditHook;
    private final ChatModelGateway chatModelGateway;
    private final KnowledgeSearchService knowledgeSearchService;
    private final QuestionParsingService questionParsingService;

    public UrbanManagementAgent(AgentScopeModelFactory agentScopeModelFactory,
                                AgentScopeProperties agentScopeProperties,
                                UrbanToolRegistry toolRegistry,
                                AgentToolAuditHook toolAuditHook,
                                ChatModelGateway chatModelGateway,
                                KnowledgeSearchService knowledgeSearchService,
                                QuestionParsingService questionParsingService) {
        this.agentScopeModelFactory = agentScopeModelFactory;
        this.agentScopeProperties = agentScopeProperties;
        this.toolRegistry = toolRegistry;
        this.toolAuditHook = toolAuditHook;
        this.chatModelGateway = chatModelGateway;
        this.knowledgeSearchService = knowledgeSearchService;
        this.questionParsingService = questionParsingService;
    }

    public AgentAnswer answer(String runId, List<String> history, String question) {
        AgentExecutionContextHolder.open(runId);
        try {
            ParsedQuestion parsedQuestion = questionParsingService.loadOrAnalyze(runId, question);
            KnowledgeContext knowledgeContext = prepareKnowledgeContext(question, parsedQuestion);
            if (knowledgeContext.requiresCitation() && knowledgeContext.hits().isEmpty()) {
                return new AgentAnswer(MISSING_CITABLE_SOURCE_MESSAGE, List.of(), null, null, null, null);
            }
            if (agentScopeModelFactory.enabled()) {
                return answerByAgentScope(history, question, knowledgeContext);
            }
            return answerByModelProvider(history, question, knowledgeContext);
        } catch (BusinessException ex) {
            if (ex.errorCode() == ErrorCode.MODEL_UNAVAILABLE) {
                throw modelUnavailable();
            }
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("UrbanManagementAgent failed. runId={}", runId, ex);
            throw modelUnavailable();
        } finally {
            AgentExecutionContextHolder.clear();
        }
    }

    private AgentAnswer answerByAgentScope(List<String> history, String question, KnowledgeContext knowledgeContext) {
        OpenAIChatModel model = agentScopeModelFactory.createChatModel();
        ReActAgent agent = ReActAgent.builder()
                .name("UrbanManagementAgent")
                .sysPrompt(agentScopeProperties.getSystemPrompt())
                .model(model)
                .toolkit(toolRegistry.createToolkit())
                .memory(new InMemoryMemory())
                .maxIters(agentScopeProperties.getMaxIters())
                .hooks(List.of(toolAuditHook))
                .build();
        Msg response = agent.call(Msg.builder().textContent(buildConversationPrompt(history, question, knowledgeContext)).build()).block();
        String content = response == null || response.getTextContent() == null
                ? "未生成有效回答。"
                : response.getTextContent();
        return new AgentAnswer(content, mergeCitations(knowledgeContext.citations(), AgentExecutionContextHolder.citations()), null, null, null, null);
    }

    private AgentAnswer answerByModelProvider(List<String> history, String question, KnowledgeContext knowledgeContext) {
        try {
            String modelResponse = sanitizeUserFacingAnswer(chatModelGateway.chat(history, buildModelPrompt(question, knowledgeContext)));
            if (modelResponse.isBlank()) {
                throw modelUnavailable();
            }
            return new AgentAnswer(modelResponse, knowledgeContext.citations(), null, null, null, null);
        } catch (BusinessException ex) {
            if (ex.errorCode() == ErrorCode.MODEL_UNAVAILABLE) {
                throw modelUnavailable();
            }
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Model answer unavailable.", ex);
            throw modelUnavailable();
        }
    }

    private String buildConversationPrompt(List<String> history, String question, KnowledgeContext knowledgeContext) {
        StringBuilder builder = new StringBuilder();
        if (!history.isEmpty()) {
            builder.append("以下是当前会话历史：\n");
            history.forEach(item -> builder.append("- ").append(item).append('\n'));
            builder.append('\n');
        }
        builder.append("用户当前问题：").append(question);
        appendReferenceMaterials(builder, knowledgeContext.hits());
        appendAnswerStyleRules(builder);
        if (knowledgeContext.requiresCitation()) {
            builder.append("\n\n回答约束：只能基于参考资料或工具检索结果回答；如资料不足，必须说明未检索到充分依据，不得编造政策、法规、条款或办理流程。");
        }
        return builder.toString();
    }

    private String buildModelPrompt(String question, KnowledgeContext knowledgeContext) {
        if (knowledgeContext.hits().isEmpty()) {
            return question;
        }
        StringBuilder builder = new StringBuilder(question);
        appendReferenceMaterials(builder, knowledgeContext.hits());
        if (knowledgeContext.requiresCitation()) {
            builder.append("\n请严格依据参考资料回答，标明可引用来源；资料不足时明确说明不足，不得补造依据。");
        } else {
            builder.append("\n请优先依据参考资料回答，并保持答案简洁。");
        }
        appendAnswerStyleRules(builder);
        return builder.toString();
    }

    private String cleanSnippet(String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return "未提取到摘要内容";
        }
        String cleaned = snippet
                .replaceAll("https?://\\S+", "")
                .replaceAll("[#*_`>]", "")
                .replaceAll("文件信息\\s*-\\s*", "")
                .replaceAll("来源链接：\\s*", "")
                .replaceAll("参考来源：\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
        cleaned = cleaned.replaceAll("^\\d+[.．、]\\s*", "");
        cleaned = cleaned.replaceAll("\\s+\\d+[.．、]\\s*", "；");
        cleaned = cleaned.replaceAll("[；;]\\s*[；;]+", "；");
        cleaned = cleaned.replaceAll("\\s+([，。；：、])", "$1");
        return limitToReadableSentence(cleaned, 220);
    }

    private KnowledgeContext prepareKnowledgeContext(String question, ParsedQuestion parsedQuestion) {
        boolean requiresCitation = parsedQuestion.requiresCitation();
        try {
            KnowledgeCategory category = resolveKnowledgeCategory(parsedQuestion);
            List<KnowledgeSearchHit> hits = collectKnowledgeHits(question, category);
            return new KnowledgeContext(hits, extractCitations(hits), requiresCitation);
        } catch (RuntimeException ex) {
            log.warn("Knowledge source preparation failed, continue with empty references.", ex);
            return new KnowledgeContext(List.of(), List.of(), requiresCitation);
        }
    }

    private List<KnowledgeSearchHit> collectKnowledgeHits(String question, KnowledgeCategory category) {
        Map<String, KnowledgeSearchHit> bestByDocument = new LinkedHashMap<>();
        if (category != null) {
            List<KnowledgeSearchHit> preferredHits = knowledgeSearchService.search(question, category, 8);
            mergeHits(bestByDocument, preferredHits);
            if (!preferredHits.isEmpty()) {
                return bestByDocument.values().stream()
                        .sorted(Comparator
                                .comparingDouble((KnowledgeSearchHit hit) -> referenceOrderingScore(hit, category))
                                .reversed()
                                .thenComparing(Comparator.comparingDouble(KnowledgeSearchHit::score).reversed()))
                        .limit(4)
                        .toList();
            }
        }
        mergeHits(bestByDocument, knowledgeSearchService.search(question, null, 8));
        return bestByDocument.values().stream()
                .sorted(Comparator
                        .comparingDouble((KnowledgeSearchHit hit) -> referenceOrderingScore(hit, category))
                        .reversed()
                        .thenComparing(Comparator.comparingDouble(KnowledgeSearchHit::score).reversed()))
                .limit(4)
                .toList();
    }

    private double referenceOrderingScore(KnowledgeSearchHit hit, KnowledgeCategory preferredCategory) {
        return hit.score() + referenceCategoryBoost(hit.category(), preferredCategory);
    }

    private double referenceCategoryBoost(KnowledgeCategory hitCategory, KnowledgeCategory preferredCategory) {
        if (hitCategory == null || preferredCategory == null) {
            return 0D;
        }
        if (hitCategory == preferredCategory) {
            return 100D;
        }
        if (preferredCategory == KnowledgeCategory.POLICY && hitCategory == KnowledgeCategory.LAW) {
            return 30D;
        }
        if (preferredCategory == KnowledgeCategory.LAW && hitCategory == KnowledgeCategory.POLICY) {
            return 20D;
        }
        return 0D;
    }

    private void mergeHits(Map<String, KnowledgeSearchHit> bestByDocument, List<KnowledgeSearchHit> hits) {
        for (KnowledgeSearchHit hit : hits) {
            bestByDocument.merge(
                    hit.documentId(),
                    hit,
                    (current, candidate) -> preferCandidateHit(current, candidate) ? candidate : current
            );
        }
    }

    private boolean preferCandidateHit(KnowledgeSearchHit current, KnowledgeSearchHit candidate) {
        if (isMetadataHit(current) && !isMetadataHit(candidate)) {
            return true;
        }
        if (!isMetadataHit(current) && isMetadataHit(candidate)) {
            return false;
        }
        return candidate.score() > current.score();
    }

    private boolean isMetadataHit(KnowledgeSearchHit hit) {
        String text = ((hit.sectionTitle() == null ? "" : hit.sectionTitle()) + " " + hit.snippet()).toLowerCase(Locale.ROOT);
        return text.contains("文件信息") || text.contains("来源链接") || text.contains("参考来源");
    }

    private List<MessageCitationView> extractCitations(List<KnowledgeSearchHit> hits) {
        Map<String, MessageCitationView> citations = new LinkedHashMap<>();
        for (KnowledgeSearchHit hit : hits) {
            MessageCitationView citation = hit.toCitation();
            citations.putIfAbsent(citation.documentId(), citation);
        }
        return List.copyOf(citations.values());
    }

    private List<MessageCitationView> mergeCitations(List<MessageCitationView> primary, List<MessageCitationView> secondary) {
        Map<String, MessageCitationView> citations = new LinkedHashMap<>();
        for (MessageCitationView citation : primary) {
            citations.putIfAbsent(citation.documentId(), citation);
        }
        for (MessageCitationView citation : secondary) {
            citations.putIfAbsent(citation.documentId(), citation);
        }
        return List.copyOf(citations.values());
    }

    private void appendReferenceMaterials(StringBuilder builder, List<KnowledgeSearchHit> hits) {
        if (hits.isEmpty()) {
            return;
        }
        builder.append("\n\n[参考资料]\n");
        for (int index = 0; index < hits.size(); index++) {
            KnowledgeSearchHit hit = hits.get(index);
            builder.append(index + 1).append(". ").append(hit.documentTitle());
            if (hit.sectionTitle() != null && !hit.sectionTitle().isBlank()) {
                builder.append(" / ").append(hit.sectionTitle());
            }
            builder.append(": ").append(cleanReferenceMaterial(hit.content())).append('\n');
        }
    }

    private void appendAnswerStyleRules(StringBuilder builder) {
        builder.append("\n\n回答风格：请直接面向业务人员作答，不要复述“已检索到资料”“结合上下文”等过程性描述；")
                .append("不要逐条原样罗列参考资料标题或片段；不要使用省略号表示截断；")
                .append("若资料只能支撑部分结论，只输出可确认的完整信息，引用来源由系统在消息下方展示。");
    }

    private String cleanReferenceMaterial(String content) {
        String cleaned = cleanSnippet(content);
        return cleaned
                .replace("...", "")
                .replace("…", "")
                .trim();
    }

    private String limitToReadableSentence(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        int boundary = -1;
        String punctuation = "。！？；";
        for (int index = Math.min(text.length(), maxLength) - 1; index >= 80; index--) {
            if (punctuation.indexOf(text.charAt(index)) >= 0) {
                boundary = index + 1;
                break;
            }
        }
        if (boundary > 0) {
            return text.substring(0, boundary).trim();
        }
        String compact = text.substring(0, maxLength)
                .replaceAll("[，、；：,;:]?\\s*$", "")
                .trim();
        return compact.isBlank() ? text.substring(0, maxLength).trim() : compact + "。";
    }

    private String sanitizeUserFacingAnswer(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replaceFirst("^(根据(现有|参考)?资料，|根据您提供的资料，|结合当前会话上下文，)", "")
                .replaceAll("\\s*[（(]参考资料：[^）)]+[）)]\\s*$", "")
                .replaceAll("\\s*[（(]来源：[^）)]+[）)]", "")
                .replace("...", "")
                .replace("…", "")
                .trim();
    }

    private BusinessException modelUnavailable() {
        return new BusinessException(ErrorCode.MODEL_UNAVAILABLE, MODEL_UNAVAILABLE_MESSAGE);
    }

    private KnowledgeCategory resolveKnowledgeCategory(ParsedQuestion parsedQuestion) {
        return parsedQuestion.preferredKnowledgeCategory().orElse(null);
    }

    private record KnowledgeContext(
            List<KnowledgeSearchHit> hits,
            List<MessageCitationView> citations,
            boolean requiresCitation
    ) {
    }
}
