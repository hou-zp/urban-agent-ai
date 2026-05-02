alter table fact_oil_fume_warning_event
    add column if not exists unit_name varchar(255);

create index if not exists idx_oil_fume_warning_event_unit_name
    on fact_oil_fume_warning_event(unit_name);
