alter table agent_message
    add column if not exists citations_json text;

create table if not exists knowledge_document (
    id varchar(64) primary key,
    title varchar(200) not null,
    category varchar(32) not null,
    source_org varchar(120),
    document_number varchar(120),
    status varchar(32) not null,
    effective_from date,
    effective_to date,
    region_code varchar(64),
    summary text,
    content text not null,
    file_name varchar(255) not null,
    mime_type varchar(120),
    create_user_id varchar(64) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    indexed_at timestamp,
    failed_reason varchar(500)
);

create index if not exists idx_knowledge_document_status_updated_at
    on knowledge_document (status, updated_at);

create table if not exists knowledge_chunk (
    id varchar(64) primary key,
    document_id varchar(64) not null,
    chunk_index integer not null,
    section_title varchar(255),
    content text not null,
    keyword_text text not null,
    embedding_vector text,
    created_at timestamp not null
);

create index if not exists idx_knowledge_chunk_document_chunk_index
    on knowledge_chunk (document_id, chunk_index);
