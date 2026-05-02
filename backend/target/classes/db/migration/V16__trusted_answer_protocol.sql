alter table query_record
    add column if not exists metric_code varchar(64);

alter table query_record
    add column if not exists metric_name varchar(120);

alter table query_record
    add column if not exists source_summary varchar(255);

alter table query_record
    add column if not exists scope_summary text;

alter table query_record
    add column if not exists data_updated_at timestamp;

alter table query_record
    add column if not exists caliber_version varchar(64);

alter table agent_message
    add column if not exists structured_answer_json text;
