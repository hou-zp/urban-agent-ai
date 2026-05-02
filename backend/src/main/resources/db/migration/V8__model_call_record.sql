create table if not exists model_call_record (
    id varchar(64) primary key,
    run_id varchar(64),
    user_id varchar(64) not null,
    provider varchar(64) not null,
    model_name varchar(120) not null,
    operation varchar(32) not null,
    status varchar(32) not null,
    prompt_tokens integer not null,
    completion_tokens integer not null,
    total_tokens integer not null,
    latency_ms bigint not null,
    error_code varchar(64),
    error_message varchar(500),
    created_at timestamp not null
);

create index if not exists idx_model_call_record_created_at
    on model_call_record (created_at);

create index if not exists idx_model_call_record_run_id
    on model_call_record (run_id);
