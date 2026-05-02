create table if not exists risk_event (
    id varchar(64) primary key,
    run_id varchar(64) not null,
    session_id varchar(64) not null,
    user_id varchar(64) not null,
    question text not null,
    risk_level varchar(32) not null,
    risk_categories varchar(255) not null,
    trigger_reason text not null,
    review_required boolean not null,
    created_at timestamp not null
);

create index if not exists idx_risk_event_run_created_at
    on risk_event (run_id, created_at);

create index if not exists idx_risk_event_session_created_at
    on risk_event (session_id, created_at);

create table if not exists legal_review (
    id varchar(64) primary key,
    risk_event_id varchar(64) not null,
    run_id varchar(64) not null,
    session_id varchar(64) not null,
    question text not null,
    draft_answer text not null,
    reviewed_answer text,
    status varchar(32) not null,
    reviewer_id varchar(64),
    review_comment text,
    reviewed_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_legal_review_status_created_at
    on legal_review (status, created_at);

alter table agent_message
    add column if not exists risk_level varchar(32);

alter table agent_message
    add column if not exists review_id varchar(64);
