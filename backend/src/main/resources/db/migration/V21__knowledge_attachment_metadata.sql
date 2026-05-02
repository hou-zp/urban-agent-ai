alter table knowledge_document
    add column if not exists attachment_file_name varchar(255);

alter table knowledge_document
    add column if not exists attachment_mime_type varchar(120);

alter table knowledge_document
    add column if not exists attachment_size_bytes bigint;
