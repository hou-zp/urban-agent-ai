package com.example.urbanagent.knowledge.application;

import com.example.urbanagent.knowledge.domain.KnowledgeChunkEmbedding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
public class KnowledgeNativeVectorStore {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeNativeVectorStore.class);

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeNativeVectorStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void syncNativeVectors(List<KnowledgeChunkEmbedding> embeddings) {
        if (embeddings == null || embeddings.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncNativeVectorsNow(embeddings);
                }
            });
            return;
        }
        syncNativeVectorsNow(embeddings);
    }

    private void syncNativeVectorsNow(List<KnowledgeChunkEmbedding> embeddings) {
        if (embeddings == null || embeddings.isEmpty() || !isPostgreSQL() || !hasNativeVectorColumn()) {
            return;
        }
        try {
            jdbcTemplate.batchUpdate(
                    """
                    update knowledge_chunk_embedding
                    set embedding_vector_pg = cast(? as vector)
                    where chunk_id = ?
                    """,
                    embeddings,
                    100,
                    (statement, embedding) -> {
                        statement.setString(1, "[" + embedding.getEmbeddingVector() + "]");
                        statement.setString(2, embedding.getChunkId());
                    }
            );
        } catch (DataAccessException ex) {
            log.warn("failed to sync pgvector native embeddings, fallback to text vector search: {}", ex.getMessage());
        }
    }

    private boolean isPostgreSQL() {
        try {
            return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
                    "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())
            ));
        } catch (DataAccessException ex) {
            return false;
        }
    }

    private boolean hasNativeVectorColumn() {
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                    """
                    select exists (
                        select 1
                        from information_schema.columns
                        where table_name = 'knowledge_chunk_embedding'
                          and column_name = 'embedding_vector_pg'
                    )
                    """,
                    Boolean.class
            );
            return Boolean.TRUE.equals(exists);
        } catch (DataAccessException ex) {
            return false;
        }
    }
}
