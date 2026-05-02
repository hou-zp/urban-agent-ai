package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.ComposedAnswer;
import com.example.urbanagent.agent.application.dto.EvidenceRef;
import com.example.urbanagent.agent.application.dto.MessageCitationView;
import com.example.urbanagent.agent.application.dto.ParsedQuestion;
import com.example.urbanagent.knowledge.application.KnowledgeSearchService;
import com.example.urbanagent.knowledge.application.dto.KnowledgeSearchHit;
import com.example.urbanagent.knowledge.domain.KnowledgeCategory;
import com.example.urbanagent.query.application.QueryApplicationService;
import com.example.urbanagent.query.application.dto.PreviewQueryRequest;
import com.example.urbanagent.query.application.dto.QueryAnswerView;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrustedAnswerService {

    private static final String MISSING_CITABLE_SOURCE_MESSAGE = "未检索到可引用的政策法规或业务依据，暂不能给出正式答复。请补充相关知识文档、缩小问题范围，或转人工核实后再使用。";

    private final QueryApplicationService queryApplicationService;
    private final KnowledgeSearchService knowledgeSearchService;
    private final AnswerComposer answerComposer;
    private final FinalGuardrailService finalGuardrailService;

    public TrustedAnswerService(QueryApplicationService queryApplicationService,
                                KnowledgeSearchService knowledgeSearchService,
                                AnswerComposer answerComposer,
                                FinalGuardrailService finalGuardrailService) {
        this.queryApplicationService = queryApplicationService;
        this.knowledgeSearchService = knowledgeSearchService;
        this.answerComposer = answerComposer;
        this.finalGuardrailService = finalGuardrailService;
    }

    public AgentAnswer answer(String runId, String question, ParsedQuestion parsedQuestion) {
        List<KnowledgeSearchHit> knowledgeHits = loadKnowledgeHits(question, parsedQuestion);
        List<MessageCitationView> citations = knowledgeHits.stream()
                .map(KnowledgeSearchHit::toCitation)
                .toList();
        List<EvidenceRef> evidenceRefs = toEvidenceRefs(citations);
        QueryAnswerView queryAnswer = queryApplicationService.answer(new PreviewQueryRequest(question), parsedQuestion);
        ComposedAnswer composedAnswer = answerComposer.compose(parsedQuestion, queryAnswer, evidenceRefs);
        return finalGuardrailService.validate(parsedQuestion, composedAnswer)
                .<AgentAnswer>map(message -> new AgentAnswer(message, citations, null, null, null, null))
                .orElseGet(() -> new AgentAnswer(composedAnswer.render(), citations, null, null, null, composedAnswer));
    }

    private List<KnowledgeSearchHit> loadKnowledgeHits(String question, ParsedQuestion parsedQuestion) {
        if (!parsedQuestion.requiresCitation()) {
            return List.of();
        }
        KnowledgeCategory category = parsedQuestion.preferredKnowledgeCategory().orElse(null);
        List<KnowledgeSearchHit> hits = knowledgeSearchService.search(question, category, 4);
        if (!hits.isEmpty()) {
            return hits;
        }
        return knowledgeSearchService.search(question, null, 4);
    }

    private List<EvidenceRef> toEvidenceRefs(List<MessageCitationView> citations) {
        Map<String, EvidenceRef> refs = new LinkedHashMap<>();
        for (MessageCitationView citation : citations) {
            refs.putIfAbsent(
                    citation.documentId(),
                    EvidenceRef.fromCitation(
                            citation.documentId(),
                            citation.documentId(),
                            citation.documentTitle(),
                            citation.sectionTitle(),
                            citation.sourceOrg(),
                            citation.effectiveFrom(),
                            citation.effectiveTo()
                    )
            );
        }
        return List.copyOf(refs.values());
    }
}
