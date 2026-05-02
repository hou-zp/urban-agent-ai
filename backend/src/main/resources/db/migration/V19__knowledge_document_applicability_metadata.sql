alter table knowledge_document
    add column if not exists security_level varchar(32) not null default 'INTERNAL';

alter table knowledge_document
    add column if not exists attachment_ref varchar(255);

alter table knowledge_document
    add column if not exists source_url varchar(500);

create index if not exists idx_knowledge_document_region_effective
    on knowledge_document (region_code, effective_from, effective_to);
