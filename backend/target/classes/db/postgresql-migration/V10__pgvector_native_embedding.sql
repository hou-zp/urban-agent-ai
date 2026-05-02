create extension if not exists vector;

alter table knowledge_chunk_embedding
    add column if not exists embedding_vector_pg vector;

update knowledge_chunk_embedding
set embedding_vector_pg = ('[' || embedding_vector || ']')::vector
where embedding_vector_pg is null
  and embedding_vector is not null
  and embedding_vector <> '';

create index if not exists idx_knowledge_chunk_embedding_vector_hnsw
    on knowledge_chunk_embedding
    using hnsw (embedding_vector_pg vector_cosine_ops)
    where embedding_vector_pg is not null;
