alter table metric_definition
    add column if not exists caliber_version varchar(64) not null default 'v1';

alter table metric_definition
    add column if not exists data_quality varchar(32) not null default 'verified';

alter table metric_definition
    add column if not exists applicable_region varchar(120) not null default '未说明';

alter table metric_definition
    add column if not exists data_updated_at timestamp not null default current_timestamp;
