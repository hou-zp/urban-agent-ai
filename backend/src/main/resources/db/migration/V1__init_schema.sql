create table if not exists agent_session (
    id varchar(64) primary key,
    user_id varchar(64) not null,
    title varchar(100) not null,
    status varchar(32) not null,
    created_at timestamp not null
);

create table if not exists agent_message (
    id varchar(64) primary key,
    session_id varchar(64) not null,
    role varchar(32) not null,
    content text not null,
    created_at timestamp not null
);

create index if not exists idx_agent_message_session_created_at
    on agent_message (session_id, created_at);

create table if not exists agent_run (
    id varchar(64) primary key,
    session_id varchar(64) not null,
    user_id varchar(64) not null,
    question text not null,
    status varchar(32) not null,
    model_name varchar(64) not null,
    created_at timestamp not null,
    completed_at timestamp
);

create table if not exists tool_call (
    id varchar(64) primary key,
    run_id varchar(64) not null,
    tool_name varchar(128) not null,
    input_summary text not null,
    output_summary text not null,
    created_at timestamp not null
);
