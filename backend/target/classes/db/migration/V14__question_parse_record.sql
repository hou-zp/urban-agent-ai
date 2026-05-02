create table if not exists question_parse_record (
    run_id varchar(64) primary key,
    original_question text not null,
    primary_intent varchar(64),
    overall_confidence double precision not null,
    requires_citation boolean not null,
    requires_data_query boolean not null,
    intents_json text not null,
    scenes_json text not null,
    slots_json text not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_question_parse_record_primary_intent
    on question_parse_record (primary_intent);
