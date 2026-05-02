package com.example.urbanagent.knowledge.repository;

import com.example.urbanagent.knowledge.domain.KnowledgeChunkEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface KnowledgeChunkEmbeddingRepository extends JpaRepository<KnowledgeChunkEmbedding, String> {

    void deleteByDocumentId(String documentId);

    List<KnowledgeChunkEmbedding> findByChunkIdIn(Collection<String> chunkIds);
}
