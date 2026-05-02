create table if not exists dim_organization (
    org_code varchar(64) primary key,
    org_name varchar(120) not null,
    org_type varchar(32) not null,
    parent_org_code varchar(64),
    region_code varchar(64) not null,
    enabled boolean not null default true,
    created_at timestamp not null default current_timestamp
);

insert into dim_organization (org_code, org_name, org_type, parent_org_code, region_code, enabled, created_at)
select 'ST-JF', '解放街道', 'STREET', 'district-a', 'district-a', true, current_timestamp
where not exists (select 1 from dim_organization where org_code = 'ST-JF');
insert into dim_organization (org_code, org_name, org_type, parent_org_code, region_code, enabled, created_at)
select 'ST-HP', '和平街道', 'STREET', 'district-a', 'district-a', true, current_timestamp
where not exists (select 1 from dim_organization where org_code = 'ST-HP');
insert into dim_organization (org_code, org_name, org_type, parent_org_code, region_code, enabled, created_at)
select 'ST-BJ', '滨江街道', 'STREET', 'district-b', 'district-b', true, current_timestamp
where not exists (select 1 from dim_organization where org_code = 'ST-BJ');
insert into dim_organization (org_code, org_name, org_type, parent_org_code, region_code, enabled, created_at)
select 'KQ-KQ', '柯桥街道', 'STREET', 'shaoxing-keqiao', 'shaoxing-keqiao', true, current_timestamp
where not exists (select 1 from dim_organization where org_code = 'KQ-KQ');
insert into dim_organization (org_code, org_name, org_type, parent_org_code, region_code, enabled, created_at)
select 'KQ-HS', '华舍街道', 'STREET', 'shaoxing-keqiao', 'shaoxing-keqiao', true, current_timestamp
where not exists (select 1 from dim_organization where org_code = 'KQ-HS');
insert into dim_organization (org_code, org_name, org_type, parent_org_code, region_code, enabled, created_at)
select 'KQ-QQ', '钱清街道', 'STREET', 'shaoxing-keqiao', 'shaoxing-keqiao', true, current_timestamp
where not exists (select 1 from dim_organization where org_code = 'KQ-QQ');
insert into dim_organization (org_code, org_name, org_type, parent_org_code, region_code, enabled, created_at)
select 'KQ-QX', '齐贤街道', 'STREET', 'shaoxing-keqiao', 'shaoxing-keqiao', true, current_timestamp
where not exists (select 1 from dim_organization where org_code = 'KQ-QX');
insert into dim_organization (org_code, org_name, org_type, parent_org_code, region_code, enabled, created_at)
select 'KQ-MA', '马鞍街道', 'STREET', 'shaoxing-keqiao', 'shaoxing-keqiao', true, current_timestamp
where not exists (select 1 from dim_organization where org_code = 'KQ-MA');

alter table fact_complaint_order add column if not exists street_code varchar(64);
alter table fact_inspection_record add column if not exists street_code varchar(64);
alter table fact_case_handle add column if not exists street_code varchar(64);
alter table fact_oil_fume_warning add column if not exists street_code varchar(64);
alter table dim_catering_unit add column if not exists street_code varchar(64);
alter table fact_oil_fume_warning_event add column if not exists street_code varchar(64);
alter table fact_oil_fume_device_inspection add column if not exists street_code varchar(64);

update fact_complaint_order set street_code = case street_name
    when '解放街道' then 'ST-JF'
    when '和平街道' then 'ST-HP'
    when '滨江街道' then 'ST-BJ'
    else street_code
end where street_code is null;
update fact_inspection_record set street_code = case street_name
    when '解放街道' then 'ST-JF'
    when '和平街道' then 'ST-HP'
    when '滨江街道' then 'ST-BJ'
    else street_code
end where street_code is null;
update fact_case_handle set street_code = case street_name
    when '解放街道' then 'ST-JF'
    when '和平街道' then 'ST-HP'
    when '滨江街道' then 'ST-BJ'
    else street_code
end where street_code is null;
update dim_catering_unit set street_code = case street_name
    when '柯桥街道' then 'KQ-KQ'
    when '华舍街道' then 'KQ-HS'
    when '钱清街道' then 'KQ-QQ'
    when '齐贤街道' then 'KQ-QX'
    when '马鞍街道' then 'KQ-MA'
    else street_code
end where street_code is null;
update fact_oil_fume_device_inspection set street_code = case street_name
    when '柯桥街道' then 'KQ-KQ'
    when '华舍街道' then 'KQ-HS'
    when '钱清街道' then 'KQ-QQ'
    when '齐贤街道' then 'KQ-QX'
    when '马鞍街道' then 'KQ-MA'
    else street_code
end where street_code is null;
update fact_oil_fume_warning_event set street_code = case street_name
    when '柯桥街道' then 'KQ-KQ'
    when '华舍街道' then 'KQ-HS'
    when '钱清街道' then 'KQ-QQ'
    when '齐贤街道' then 'KQ-QX'
    when '马鞍街道' then 'KQ-MA'
    else street_code
end where street_code is null;
update fact_oil_fume_warning set street_code = case street_name
    when '柯桥街道' then 'KQ-KQ'
    when '华舍街道' then 'KQ-HS'
    when '钱清街道' then 'KQ-QQ'
    when '齐贤街道' then 'KQ-QX'
    when '马鞍街道' then 'KQ-MA'
    else street_code
end where street_code is null;

alter table fact_complaint_order alter column street_name drop not null;
alter table fact_inspection_record alter column street_name drop not null;
alter table fact_case_handle alter column street_name drop not null;
alter table fact_oil_fume_warning alter column street_name drop not null;
alter table dim_catering_unit alter column street_name drop not null;
alter table fact_oil_fume_warning_event alter column street_name drop not null;
alter table fact_oil_fume_device_inspection alter column street_name drop not null;

insert into data_field (id, table_id, field_name, business_name, data_type, sensitive_level, visible_roles, created_at, updated_at)
select random_uuid(), t.id, 'street_code', '街道编码', 'varchar', 'PUBLIC', 'ADMIN,OFFICER,MANAGER', current_timestamp, current_timestamp
from data_table t
where t.table_name in ('fact_complaint_order', 'fact_inspection_record')
  and not exists (select 1 from data_field f where f.table_id = t.id and f.field_name = 'street_code');
insert into data_field (id, table_id, field_name, business_name, data_type, sensitive_level, visible_roles, created_at, updated_at)
select random_uuid(), t.id, 'street_code', '街道编码', 'varchar', 'PUBLIC', 'ADMIN,MANAGER', current_timestamp, current_timestamp
from data_table t
where t.table_name = 'fact_case_handle'
  and not exists (select 1 from data_field f where f.table_id = t.id and f.field_name = 'street_code');
insert into data_field (id, table_id, field_name, business_name, data_type, sensitive_level, visible_roles, created_at, updated_at)
select random_uuid(), t.id, 'street_code', '镇街编码', 'varchar', 'PUBLIC', 'ADMIN,OFFICER,MANAGER', current_timestamp, current_timestamp
from data_table t
where t.table_name in ('fact_oil_fume_warning', 'dim_catering_unit', 'fact_oil_fume_warning_event', 'fact_oil_fume_device_inspection')
  and not exists (select 1 from data_field f where f.table_id = t.id and f.field_name = 'street_code');
insert into data_field (id, table_id, field_name, business_name, data_type, sensitive_level, visible_roles, created_at, updated_at)
select random_uuid(), t.id, 'region_code', '区域编码', 'varchar', 'PUBLIC', 'ADMIN,OFFICER,MANAGER', current_timestamp, current_timestamp
from data_table t
where t.table_name in ('fact_oil_fume_warning', 'fact_oil_fume_warning_event')
  and not exists (select 1 from data_field f where f.table_id = t.id and f.field_name = 'region_code');

update metric_definition
set common_dimensions = replace(common_dimensions, 'street_name', 'street_code'),
    updated_at = current_timestamp
where common_dimensions like '%street_name%';
