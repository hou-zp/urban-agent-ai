package com.example.urbanagent.knowledge.application;

import com.example.urbanagent.ai.application.EmbeddingGateway;
import com.example.urbanagent.audit.application.AuditLogService;
import com.example.urbanagent.common.async.AsyncTaskDispatchService;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.example.urbanagent.knowledge.application.dto.KnowledgeDocumentView;
import com.example.urbanagent.knowledge.application.dto.KnowledgeSearchHitView;
import com.example.urbanagent.knowledge.application.dto.UpdateKnowledgeStatusRequest;
import com.example.urbanagent.knowledge.application.dto.UploadKnowledgeDocumentRequest;
import com.example.urbanagent.knowledge.domain.KnowledgeCategory;
import com.example.urbanagent.knowledge.domain.KnowledgeChunk;
import com.example.urbanagent.knowledge.domain.KnowledgeChunkEmbedding;
import com.example.urbanagent.knowledge.domain.KnowledgeDocument;
import com.example.urbanagent.knowledge.domain.KnowledgeSecurityLevel;
import com.example.urbanagent.knowledge.repository.KnowledgeChunkEmbeddingRepository;
import com.example.urbanagent.knowledge.repository.KnowledgeChunkRepository;
import com.example.urbanagent.knowledge.repository.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class KnowledgeApplicationService {

    private static final int CHUNK_SIZE = 320;
    private static final Logger log = LoggerFactory.getLogger(KnowledgeApplicationService.class);

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeChunkEmbeddingRepository embeddingRepository;
    private final KnowledgeViewMapper viewMapper;
    private final KnowledgeSearchService searchService;
    private final EmbeddingGateway embeddingGateway;
    private final KnowledgeUploadSecurityService knowledgeUploadSecurityService;
    private final KnowledgeNativeVectorStore nativeVectorStore;
    private final AuditLogService auditLogService;
    private final AsyncTaskDispatchService asyncTaskDispatchService;

    public KnowledgeApplicationService(KnowledgeDocumentRepository documentRepository,
                                       KnowledgeChunkRepository chunkRepository,
                                       KnowledgeChunkEmbeddingRepository embeddingRepository,
                                       KnowledgeViewMapper viewMapper,
                                       KnowledgeSearchService searchService,
                                       EmbeddingGateway embeddingGateway,
                                       KnowledgeUploadSecurityService knowledgeUploadSecurityService,
                                       KnowledgeNativeVectorStore nativeVectorStore,
                                       AuditLogService auditLogService,
                                       AsyncTaskDispatchService asyncTaskDispatchService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingRepository = embeddingRepository;
        this.viewMapper = viewMapper;
        this.searchService = searchService;
        this.embeddingGateway = embeddingGateway;
        this.knowledgeUploadSecurityService = knowledgeUploadSecurityService;
        this.nativeVectorStore = nativeVectorStore;
        this.auditLogService = auditLogService;
        this.asyncTaskDispatchService = asyncTaskDispatchService;
    }

    @Transactional
    public KnowledgeDocumentView uploadDocument(UploadKnowledgeDocumentRequest request) {
        String content = knowledgeUploadSecurityService.validateAndRead(request.getFile());
        KnowledgeDocument document = new KnowledgeDocument(
                request.getTitle(),
                request.getCategory(),
                request.getSourceOrg(),
                request.getDocumentNumber(),
                request.getSecurityLevel(),
                request.getEffectiveFrom(),
                request.getEffectiveTo(),
                request.getRegionCode(),
                request.getSummary(),
                request.getAttachmentRef(),
                request.getSourceUrl(),
                content,
                request.getFile().getOriginalFilename() == null ? "document.txt" : request.getFile().getOriginalFilename(),
                request.getFile().getContentType(),
                UserContextHolder.get().userId()
        );
        KnowledgeDocument saved = documentRepository.save(document);
        auditLogService.recordKnowledgeDocumentUploaded(saved);
        asyncTaskDispatchService.publishKnowledgeDocumentIndexRequested(saved);
        return viewMapper.toView(saved);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocumentView> listDocuments() {
        return documentRepository.findTop50ByOrderByUpdatedAtDesc()
                .stream()
                .map(viewMapper::toView)
                .toList();
    }

    @Transactional
    public KnowledgeDocumentView indexDocument(String documentId) {
        Instant startedAt = Instant.now();
        KnowledgeDocument document = getDocument(documentId);
        document.markIndexing();
        documentRepository.save(document);
        embeddingRepository.deleteByDocumentId(documentId);
        chunkRepository.deleteByDocumentId(documentId);

        try {
            List<KnowledgeChunk> chunks = splitDocument(document.getId(), document.getContent());
            List<KnowledgeChunk> savedChunks = chunkRepository.saveAll(chunks);
            saveEmbeddings(savedChunks);
            document.markActive();
        } catch (Exception ex) {
            document.markFailed(ex.getMessage());
        }
        KnowledgeDocument saved = documentRepository.save(document);
        auditLogService.recordKnowledgeDocumentIndexed(saved, Math.max(0L, Instant.now().toEpochMilli() - startedAt.toEpochMilli()));
        return viewMapper.toView(saved);
    }

    @Transactional
    public KnowledgeDocumentView updateStatus(String documentId, UpdateKnowledgeStatusRequest request) {
        KnowledgeDocument document = getDocument(documentId);
        document.updateStatus(request.status());
        KnowledgeDocument saved = documentRepository.save(document);
        auditLogService.recordKnowledgeDocumentStatusUpdated(saved);
        return viewMapper.toView(saved);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSearchHitView> search(String query, String category, int limit) {
        return searchService.search(query, parseCategory(category), Math.max(1, Math.min(limit, 10)))
                .stream()
                .map(viewMapper::toView)
                .toList();
    }

    @Transactional
    public void seedDemoDocuments() {
        if (!documentRepository.existsByDocumentNumber("城管规〔2026〕8号")) {
            importTextDocument(
                    "城市管理行政执法巡查制度",
                    KnowledgeCategory.POLICY,
                    "市城管局",
                    "城管规〔2026〕8号",
                    KnowledgeSecurityLevel.PUBLIC,
                    LocalDate.of(2026, 1, 1),
                    null,
                    "city",
                    "占道经营巡查和投诉响应要求",
                    null,
                    "https://example.gov.cn/policy/cg-2026-8",
                    """
                    城市管理行政执法巡查制度
                    对占道经营问题，应当按照巡查发现、现场劝导、复查闭环的流程处置。
                    对群众投诉事项，应当在两个工作日内完成首次响应。
                    对重点区域应当开展日常巡查并留痕。
                    """,
                    "seed-policy.txt"
            );
        }
        if (!documentRepository.existsByDocumentNumber("城管法〔2026〕3号")) {
            importTextDocument(
                    "城市市容和环境卫生管理执法指引",
                    KnowledgeCategory.LAW,
                    "市城管执法监督处",
                    "城管法〔2026〕3号",
                    KnowledgeSecurityLevel.PUBLIC,
                    LocalDate.of(2026, 1, 1),
                    null,
                    "city",
                    "执法依据、处罚裁量和证据要求",
                    null,
                    "https://example.gov.cn/law/cg-2026-3",
                    """
                    城市市容和环境卫生管理执法指引
                    对占道经营、乱堆物料等行为，应当先核实事实、固定证据、确认适用条款。
                    涉及处罚金额、强制措施、拆除决定的答复，必须结合法条、裁量基准和案件事实审慎处理。
                    涉及行政复议、行政诉讼的答复，不得替代正式法制审核意见。
                    """,
                    "seed-law.txt"
            );
        }
        if (!documentRepository.existsByDocumentNumber("城管业〔2026〕12号")) {
            importTextDocument(
                    "城管热线投诉处置流程",
                    KnowledgeCategory.BUSINESS,
                    "市数字城管中心",
                    "城管业〔2026〕12号",
                    KnowledgeSecurityLevel.INTERNAL,
                    LocalDate.of(2026, 1, 1),
                    null,
                    "city",
                    "热线投诉受理、派遣、核查、反馈流程",
                    "knowledge://attachments/hotline-process-v2026",
                    null,
                    """
                    城管热线投诉处置流程
                    热线投诉应当先完成受理登记，再按区域和事项派遣责任单位。
                    一般事项应当在两个工作日内首次响应，复杂事项应当说明原因并持续跟进。
                    办结前应当完成核查和回访，确保结果可追溯。
                    """,
                    "seed-business.txt"
            );
        }
        if (!documentRepository.existsByDocumentNumber("城管地块〔2026〕5号")) {
            importTextDocument(
                    "空闲地块整治和利用管理指引",
                    KnowledgeCategory.POLICY,
                    "柯桥区城市管理局",
                    "城管地块〔2026〕5号",
                    KnowledgeSecurityLevel.PUBLIC,
                    LocalDate.of(2026, 1, 1),
                    null,
                    "shaoxing-keqiao",
                    "空闲地块排查、整治、管护和临时利用要求",
                    null,
                    "https://example.gov.cn/policy/idle-land-2026-5",
                    """
                    空闲地块整治和利用管理指引
                    空闲地块应当先建立台账，记录位置、面积、权属、现状问题、责任单位和整改期限。
                    对存在垃圾堆放、裸土扬尘、围挡破损、积水蚊蝇等问题的空闲地块，应当组织清理、覆盖、修复围挡并落实日常保洁。
                    对具备条件的空闲地块，可以依法依规用于临时停车、口袋公园、便民服务点或应急疏散空间，但不得改变土地权属和规划用途。
                    属于自然资源领域闲置土地认定、处置或收回的事项，应当转自然资源部门按法定程序办理；城管答复只说明市容环境整治和协同处置要求。
                    """,
                    "seed-idle-land-policy.txt"
            );
        }
    }

    @Transactional
    public void resetAndSeedDemoDocuments() {
        embeddingRepository.deleteAllInBatch();
        chunkRepository.deleteAllInBatch();
        documentRepository.deleteAllInBatch();
        seedDemoDocuments();
    }

    private KnowledgeDocument getDocument(String documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND));
    }

    private void importTextDocument(String title,
                                    KnowledgeCategory category,
                                    String sourceOrg,
                                    String documentNumber,
                                    KnowledgeSecurityLevel securityLevel,
                                    LocalDate effectiveFrom,
                                    LocalDate effectiveTo,
                                    String regionCode,
                                    String summary,
                                    String attachmentRef,
                                    String sourceUrl,
                                    String content,
                                    String fileName) {
        KnowledgeDocument document = new KnowledgeDocument(
                title,
                category,
                sourceOrg,
                documentNumber,
                securityLevel,
                effectiveFrom,
                effectiveTo,
                regionCode,
                summary,
                attachmentRef,
                sourceUrl,
                content.trim(),
                fileName,
                "text/plain",
                "system-seed"
        );
        document.markIndexing();
        KnowledgeDocument saved = documentRepository.save(document);
        List<KnowledgeChunk> savedChunks = chunkRepository.saveAll(splitDocument(saved.getId(), saved.getContent()));
        saveEmbeddings(savedChunks);
        saved.markActive();
        documentRepository.save(saved);
    }

    private List<KnowledgeChunk> splitDocument(String documentId, String content) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        List<String> paragraphs = content.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
        if (paragraphs.isEmpty()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_PARSE_FAILED, "knowledge document has no valid content");
        }

        StringBuilder buffer = new StringBuilder();
        int chunkIndex = 0;
        for (String paragraph : paragraphs) {
            if (buffer.length() > 0 && buffer.length() + paragraph.length() + 1 > CHUNK_SIZE) {
                chunks.add(createChunk(documentId, chunkIndex++, buffer.toString()));
                buffer.setLength(0);
            }
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append(paragraph);
        }
        if (buffer.length() > 0) {
            chunks.add(createChunk(documentId, chunkIndex, buffer.toString()));
        }
        return chunks;
    }

    private KnowledgeChunk createChunk(String documentId, int chunkIndex, String content) {
        String normalized = normalize(content);
        String sectionTitle = buildSectionTitle(content);
        return new KnowledgeChunk(
                documentId,
                chunkIndex,
                sectionTitle,
                content,
                normalized
        );
    }

    private List<KnowledgeChunkEmbedding> createEmbeddings(List<KnowledgeChunk> chunks) {
        List<KnowledgeChunkEmbedding> embeddings = new ArrayList<>();
        boolean embeddingUnavailable = false;
        for (KnowledgeChunk chunk : chunks) {
            if (embeddingUnavailable) {
                break;
            }
            try {
                KnowledgeChunkEmbedding embedding = createEmbedding(chunk);
                if (embedding != null) {
                    embeddings.add(embedding);
                }
            } catch (RuntimeException ex) {
                embeddingUnavailable = true;
                log.warn("skip embedding for knowledge chunk {}, fallback to keyword retrieval: {}", chunk.getId(), ex.getMessage());
            }
        }
        if (embeddingUnavailable) {
            int skipped = Math.max(0, chunks.size() - embeddings.size());
            log.info("skip embedding for remaining {} knowledge chunks because embedding service is unavailable", skipped);
        }
        return embeddings;
    }

    private void saveEmbeddings(List<KnowledgeChunk> savedChunks) {
        List<KnowledgeChunkEmbedding> embeddings = createEmbeddings(savedChunks);
        if (embeddings.isEmpty()) {
            return;
        }
        List<KnowledgeChunkEmbedding> savedEmbeddings = embeddingRepository.saveAllAndFlush(embeddings);
        nativeVectorStore.syncNativeVectors(savedEmbeddings);
    }

    private KnowledgeChunkEmbedding createEmbedding(KnowledgeChunk chunk) {
        float[] vector = embeddingGateway.embed(chunk.getContent());
        String serialized = EmbeddingVectorCodec.serialize(vector);
        if (serialized == null) {
            return null;
        }
        return new KnowledgeChunkEmbedding(
                chunk.getId(),
                chunk.getDocumentId(),
                embeddingGateway.embeddingModelName(),
                vector.length,
                serialized
        );
    }

    private String normalize(String content) {
        return content.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private String buildSectionTitle(String content) {
        String title = content.replaceAll("\\s+", " ").trim();
        return title.length() <= 30 ? title : title.substring(0, 30);
    }

    private KnowledgeCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        try {
            return KnowledgeCategory.valueOf(category.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "unknown knowledge category");
        }
    }
}
