package com.example.urbanagent.knowledge.repository;

import com.example.urbanagent.knowledge.domain.KnowledgeDocument;
import com.example.urbanagent.knowledge.domain.KnowledgeDocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, String> {

    List<KnowledgeDocument> findTop50ByOrderByUpdatedAtDesc();

    List<KnowledgeDocument> findByStatusIn(Collection<KnowledgeDocumentStatus> statuses);

    boolean existsByDocumentNumber(String documentNumber);
}
