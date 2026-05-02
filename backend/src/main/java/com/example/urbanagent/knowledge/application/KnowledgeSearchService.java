package com.example.urbanagent.knowledge.application;

import com.example.urbanagent.ai.application.EmbeddingGateway;
import com.example.urbanagent.knowledge.application.dto.KnowledgeSearchHit;
import com.example.urbanagent.knowledge.domain.KnowledgeCategory;
import com.example.urbanagent.knowledge.domain.KnowledgeChunk;
import com.example.urbanagent.knowledge.domain.KnowledgeChunkEmbedding;
import com.example.urbanagent.knowledge.domain.KnowledgeDocument;
import com.example.urbanagent.knowledge.domain.KnowledgeDocumentStatus;
import com.example.urbanagent.knowledge.repository.KnowledgeChunkEmbeddingRepository;
import com.example.urbanagent.knowledge.repository.KnowledgeChunkRepository;
import com.example.urbanagent.knowledge.repository.KnowledgeDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class KnowledgeSearchService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final double VECTOR_ONLY_THRESHOLD = 0.78D;
    private static final double VECTOR_SCORE_WEIGHT = 3D;
    private static final double MIN_KEYWORD_SCORE_WITHOUT_VECTOR_MATCH = 5D;
    private static final double KEQIAO_REGION_BOOST = 2.5D;
    private static final double KEQIAO_EXPLICIT_REGION_BOOST = 4.5D;
    private static final double SHAOXING_REGION_BOOST = 1.2D;
    private static final double SHAOXING_EXPLICIT_REGION_BOOST = 2.5D;
    private static final double TOPIC_MATCH_BOOST = 12D;
    private static final double TOPIC_SUPPORT_BOOST = 3D;
    private static final double TOPIC_MISS_PENALTY = 10D;
    private static final Set<String> KEQIAO_REGION_CODES = Set.of("shaoxing-keqiao", "keqiao");
    private static final Set<String> SHAOXING_REGION_CODES = Set.of("shaoxing-city", "shaoxing");

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeChunkEmbeddingRepository embeddingRepository;
    private final EmbeddingGateway embeddingGateway;
    private final KnowledgeApplicabilityService knowledgeApplicabilityService;

    public KnowledgeSearchService(KnowledgeDocumentRepository documentRepository,
                                  KnowledgeChunkRepository chunkRepository,
                                  KnowledgeChunkEmbeddingRepository embeddingRepository,
                                  EmbeddingGateway embeddingGateway,
                                  KnowledgeApplicabilityService knowledgeApplicabilityService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingRepository = embeddingRepository;
        this.embeddingGateway = embeddingGateway;
        this.knowledgeApplicabilityService = knowledgeApplicabilityService;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSearchHit> search(String query, KnowledgeCategory category, int limit) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        List<KnowledgeDocument> activeDocuments = documentRepository.findByStatusIn(activeStatuses())
                .stream()
                .filter(document -> category == null || document.getCategory() == category)
                .filter(document -> knowledgeApplicabilityService.isSearchable(document, query))
                .toList();
        if (activeDocuments.isEmpty()) {
            return List.of();
        }

        Map<String, KnowledgeDocument> documentMap = new HashMap<>();
        for (KnowledgeDocument document : activeDocuments) {
            documentMap.put(document.getId(), document);
        }

        List<KnowledgeChunk> chunks = chunkRepository.findByDocumentIdIn(documentMap.keySet());
        Map<String, KnowledgeChunkEmbedding> embeddingMap = embeddingRepository.findByChunkIdIn(
                        chunks.stream().map(KnowledgeChunk::getId).toList()
                )
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        KnowledgeChunkEmbedding::getChunkId,
                        embedding -> embedding,
                        (left, right) -> left
                ));
        float[] queryEmbedding = safeEmbed(query);

        return chunks.stream()
                .map(chunk -> scoreChunk(
                        chunk,
                        documentMap.get(chunk.getDocumentId()),
                        normalizedQuery,
                        queryEmbedding,
                        embeddingMap.get(chunk.getId())
                ))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(KnowledgeSearchHit::score).reversed())
                .limit(limit)
                .toList();
    }

    private KnowledgeSearchHit scoreChunk(KnowledgeChunk chunk,
                                          KnowledgeDocument document,
                                          String query,
                                          float[] queryEmbedding,
                                          KnowledgeChunkEmbedding chunkEmbedding) {
        if (document == null) {
            return null;
        }
        String keywordText = chunk.getKeywordText();
        String normalizedDocumentText = normalize(
                document.getTitle() + " "
                        + nullSafe(document.getFileName()) + " "
                        + nullSafe(chunk.getSectionTitle()) + " "
                        + nullSafe(chunk.getKeywordText()) + " "
                        + nullSafe(chunk.getContent())
        );
        if (hasExplicitTopicMismatch(normalizedDocumentText, query)) {
            return null;
        }
        double keywordScore = computeKeywordScore(keywordText, query);
        double vectorScore = computeVectorScore(queryEmbedding, chunkEmbedding);
        double localityScore = computeLocalityScore(document.getRegionCode(), query);
        double topicScore = computeTopicScore(normalizedDocumentText, query);
        double score = keywordScore + Math.max(0D, vectorScore) * VECTOR_SCORE_WEIGHT + localityScore + topicScore;
        if (score <= 0D) {
            return null;
        }
        if (keywordScore <= 0D && vectorScore < VECTOR_ONLY_THRESHOLD) {
            return null;
        }
        if (keywordScore < MIN_KEYWORD_SCORE_WITHOUT_VECTOR_MATCH && vectorScore < VECTOR_ONLY_THRESHOLD) {
            return null;
        }
        return new KnowledgeSearchHit(
                document.getId(),
                document.getTitle(),
                document.getFileName(),
                document.getCategory(),
                document.getSourceOrg(),
                document.getDocumentNumber(),
                document.getSecurityLevel().name(),
                document.getRegionCode(),
                document.getSourceUrl(),
                chunk.getSectionTitle(),
                buildSnippet(chunk.getContent(), query),
                chunk.getContent(),
                score,
                document.getEffectiveFrom(),
                document.getEffectiveTo()
        );
    }

    private double computeTopicScore(String normalizedDocumentText, String query) {
        double score = 0D;
        if (containsAny(query, "空闲地块", "闲置地块")) {
            score += scoreTopic(
                    normalizedDocumentText,
                    List.of("空闲地块", "闲置地块", "闲置土地", "地块"),
                    List.of("围挡", "裸土", "垃圾", "权属", "治理")
            );
        }
        if (containsAny(query, "投诉") && containsAny(query, "排行", "排名")) {
            score += scoreTopic(
                    normalizedDocumentText,
                    List.of("投诉", "排行", "排名"),
                    List.of("街道", "高频", "处置", "整改", "重复投诉", "时效")
            );
        }
        return score;
    }

    private boolean hasExplicitTopicMismatch(String normalizedDocumentText, String query) {
        if (containsAny(query, "空闲地块", "闲置地块")
                && !containsAny(normalizedDocumentText, "空闲地块", "闲置地块", "闲置土地", "地块")) {
            return true;
        }
        if (containsAny(query, "投诉") && containsAny(query, "排行", "排名")
                && !containsAny(normalizedDocumentText, "投诉", "排行", "排名", "街道", "重复投诉", "时效")) {
            return true;
        }
        return false;
    }

    private double scoreTopic(String normalizedDocumentText, List<String> primaryTerms, List<String> supportTerms) {
        boolean primaryMatched = containsAny(normalizedDocumentText, primaryTerms);
        double score = primaryMatched ? TOPIC_MATCH_BOOST : -TOPIC_MISS_PENALTY;
        if (primaryMatched && containsAny(normalizedDocumentText, supportTerms)) {
            score += TOPIC_SUPPORT_BOOST;
        }
        return score;
    }

    private double computeKeywordScore(String keywordText, String query) {
        String normalizedText = normalize(keywordText);
        if (normalizedText.isBlank()) {
            return 0D;
        }
        double score = countMatches(normalizedText, query) * 10D;
        for (String token : queryTokens(query)) {
            score += countMatches(normalizedText, token);
        }
        return score;
    }

    private double computeVectorScore(float[] queryEmbedding, KnowledgeChunkEmbedding chunkEmbedding) {
        if (chunkEmbedding == null) {
            return 0D;
        }
        return EmbeddingVectorCodec.cosineSimilarity(
                queryEmbedding,
                EmbeddingVectorCodec.deserialize(chunkEmbedding.getEmbeddingVector())
        );
    }

    private double computeLocalityScore(String regionCode, String query) {
        String normalizedRegion = normalize(regionCode);
        if (normalizedRegion.isBlank()) {
            return 0D;
        }
        boolean mentionsKeqiao = query.contains("柯桥");
        boolean mentionsShaoxing = mentionsKeqiao || query.contains("绍兴");
        if (KEQIAO_REGION_CODES.contains(normalizedRegion)) {
            return mentionsKeqiao ? KEQIAO_EXPLICIT_REGION_BOOST : KEQIAO_REGION_BOOST;
        }
        if (SHAOXING_REGION_CODES.contains(normalizedRegion)) {
            return mentionsShaoxing ? SHAOXING_EXPLICIT_REGION_BOOST : SHAOXING_REGION_BOOST;
        }
        return 0D;
    }

    private float[] safeEmbed(String query) {
        try {
            return embeddingGateway.embed(query);
        } catch (RuntimeException ex) {
            return new float[0];
        }
    }

    private List<String> queryTokens(String query) {
        if (query.contains(" ")) {
            return WHITESPACE.splitAsStream(query)
                    .filter(token -> token.length() >= 2)
                    .toList();
        }
        if (query.length() <= 4) {
            return List.of(query);
        }
        return java.util.stream.IntStream.range(0, query.length() - 1)
                .mapToObj(index -> query.substring(index, index + 2))
                .distinct()
                .toList();
    }

    private int countMatches(String text, String token) {
        if (token.isBlank()) {
            return 0;
        }
        int count = 0;
        int start = 0;
        while (start >= 0) {
            start = text.indexOf(token, start);
            if (start >= 0) {
                count++;
                start += token.length();
            }
        }
        return count;
    }

    private String buildSnippet(String content, String query) {
        String plain = content.replace('\n', ' ').trim();
        if (plain.length() <= 140) {
            return plain;
        }
        int index = normalize(plain).indexOf(query);
        if (index < 0) {
            return plain.substring(0, 140) + "...";
        }
        int start = Math.max(0, index - 30);
        int end = Math.min(plain.length(), index + 80);
        String snippet = plain.substring(start, end);
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < plain.length()) {
            snippet = snippet + "...";
        }
        return snippet;
    }

    private Collection<KnowledgeDocumentStatus> activeStatuses() {
        return List.of(KnowledgeDocumentStatus.ACTIVE, KnowledgeDocumentStatus.EXPIRED);
    }

    private String normalize(String value) {
        return value == null ? "" : WHITESPACE.matcher(value).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... tokens) {
        return containsAny(text, List.of(tokens));
    }

    private boolean containsAny(String text, List<String> tokens) {
        return tokens.stream().anyMatch(token -> !token.isBlank() && text.contains(token));
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
