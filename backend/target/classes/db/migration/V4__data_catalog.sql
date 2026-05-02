create table if not exists data_source (
    id varchar(64) primary key,
    name varchar(120) not null,
    type varchar(32) not null,
    connection_ref varchar(120) not null,
    read_only boolean not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists data_table (
    id varchar(64) primary key,
    data_source_id varchar(64) not null,
    table_name varchar(120) not null,
    business_name varchar(120) not null,
    permission_tag varchar(64) not null,
    region_code varchar(64),
    enabled boolean not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_data_table_source_region
    on data_table (data_source_id, region_code);

create table if not exists data_field (
    id varchar(64) primary key,
    table_id varchar(64) not null,
    field_name varchar(120) not null,
    business_name varchar(120) not null,
    data_type varchar(32) not null,
    sensitive_level varchar(32) not null,
    visible_roles varchar(255) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_data_field_table
    on data_field (table_id);

create table if not exists metric_definition (
    id varchar(64) primary key,
    metric_code varchar(64) not null,
    metric_name varchar(120) not null,
    description text not null,
    aggregation_expr varchar(255) not null,
    default_time_field varchar(120) not null,
    common_dimensions varchar(255) not null,
    table_name varchar(120) not null,
    region_code varchar(64),
    enabled boolean not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_metric_definition_code
    on metric_definition (metric_code);

create table if not exists query_record (
    id varchar(64) primary key,
    user_id varchar(64) not null,
    question text not null,
    candidate_sql text,
    executed_sql_summary text,
    permission_rewrite text,
    result_summary text,
    status varchar(32) not null,
    created_at timestamp not null
);
