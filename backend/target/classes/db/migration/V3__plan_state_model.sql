create table if not exists plan (
    id varchar(64) primary key,
    run_id varchar(64) not null,
    goal text not null,
    status varchar(32) not null,
    confirm_status varchar(32) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_plan_run_id
    on plan (run_id);

create table if not exists plan_step (
    id varchar(64) primary key,
    plan_id varchar(64) not null,
    step_order integer not null,
    name varchar(120) not null,
    goal text not null,
    status varchar(32) not null,
    dependency_step_ids varchar(500),
    output_summary text,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_plan_step_plan_id_step_order
    on plan_step (plan_id, step_order);
