package com.example.urbanagent.knowledge.repository;

import com.example.urbanagent.knowledge.domain.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, String> {

    void deleteByDocumentId(String documentId);

    List<KnowledgeChunk> findByDocumentIdIn(Collection<String> documentIds);
}
