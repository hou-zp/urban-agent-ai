create table if not exists knowledge_chunk_embedding (
    chunk_id varchar(64) primary key,
    document_id varchar(64) not null,
    embedding_model varchar(120) not null,
    vector_dimension integer not null,
    embedding_vector text not null,
    created_at timestamp not null
);

create index if not exists idx_knowledge_chunk_embedding_document
    on knowledge_chunk_embedding (document_id);

insert into knowledge_chunk_embedding (
    chunk_id,
    document_id,
    embedding_model,
    vector_dimension,
    embedding_vector,
    created_at
)
select
    c.id,
    c.document_id,
    'legacy-inline',
    0,
    c.embedding_vector,
    c.created_at
from knowledge_chunk c
where c.embedding_vector is not null
  and not exists (
      select 1
      from knowledge_chunk_embedding e
      where e.chunk_id = c.id
  );
