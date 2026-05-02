create table if not exists audit_log (
    id varchar(64) primary key,
    user_id varchar(64) not null,
    action_type varchar(64) not null,
    resource_type varchar(64) not null,
    resource_id varchar(64),
    session_id varchar(64),
    run_id varchar(64),
    task_id varchar(64),
    tool_call_id varchar(64),
    query_id varchar(64),
    evidence_id varchar(64),
    risk_event_id varchar(64),
    sql_summary text,
    risk_level varchar(32),
    status varchar(32),
    duration_ms bigint,
    detail_json text,
    created_at timestamp not null default current_timestamp
);

create index if not exists idx_audit_log_session_created
    on audit_log (session_id, created_at desc);

create index if not exists idx_audit_log_run_created
    on audit_log (run_id, created_at desc);

create index if not exists idx_audit_log_query_created
    on audit_log (query_id, created_at desc);

create index if not exists idx_audit_log_evidence_created
    on audit_log (evidence_id, created_at desc);

create index if not exists idx_audit_log_risk_created
    on audit_log (risk_event_id, created_at desc);
