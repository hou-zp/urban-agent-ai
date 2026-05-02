create table if not exists iam_role (
    role_code varchar(32) primary key,
    role_name varchar(64) not null,
    description varchar(255),
    enabled boolean not null,
    created_at timestamp not null
);

create table if not exists iam_region (
    region_code varchar(64) primary key,
    region_name varchar(120) not null,
    parent_region_code varchar(64),
    enabled boolean not null,
    created_at timestamp not null
);

create table if not exists iam_user (
    id varchar(64) primary key,
    display_name varchar(120) not null,
    role_code varchar(32) not null,
    region_code varchar(64) not null,
    enabled boolean not null,
    created_at timestamp not null,
    constraint fk_iam_user_role foreign key (role_code) references iam_role (role_code),
    constraint fk_iam_user_region foreign key (region_code) references iam_region (region_code)
);

insert into iam_role (role_code, role_name, description, enabled, created_at)
select 'ADMIN', '系统管理员', '系统配置、数据和审计管理', true, current_timestamp
where not exists (select 1 from iam_role where role_code = 'ADMIN');
insert into iam_role (role_code, role_name, description, enabled, created_at)
select 'OFFICER', '一线执法人员', '政策咨询、业务咨询和授权区域问数', true, current_timestamp
where not exists (select 1 from iam_role where role_code = 'OFFICER');
insert into iam_role (role_code, role_name, description, enabled, created_at)
select 'WINDOW', '窗口人员', '窗口咨询和办事流程查询', true, current_timestamp
where not exists (select 1 from iam_role where role_code = 'WINDOW');
insert into iam_role (role_code, role_name, description, enabled, created_at)
select 'MANAGER', '管理人员', '综合分析、指标问数和部门管理', true, current_timestamp
where not exists (select 1 from iam_role where role_code = 'MANAGER');
insert into iam_role (role_code, role_name, description, enabled, created_at)
select 'LEGAL', '法制审核人员', '高风险执法建议审核', true, current_timestamp
where not exists (select 1 from iam_role where role_code = 'LEGAL');
insert into iam_role (role_code, role_name, description, enabled, created_at)
select 'AUDITOR', '审计人员', '审计查询和安全核查', true, current_timestamp
where not exists (select 1 from iam_role where role_code = 'AUDITOR');

insert into iam_region (region_code, region_name, parent_region_code, enabled, created_at)
select 'city', '市级', null, true, current_timestamp
where not exists (select 1 from iam_region where region_code = 'city');
insert into iam_region (region_code, region_name, parent_region_code, enabled, created_at)
select 'district-a', 'A 区', 'city', true, current_timestamp
where not exists (select 1 from iam_region where region_code = 'district-a');
insert into iam_region (region_code, region_name, parent_region_code, enabled, created_at)
select 'district-b', 'B 区', 'city', true, current_timestamp
where not exists (select 1 from iam_region where region_code = 'district-b');

insert into iam_user (id, display_name, role_code, region_code, enabled, created_at)
select 'demo-user', '演示管理员', 'ADMIN', 'city', true, current_timestamp
where not exists (select 1 from iam_user where id = 'demo-user');
insert into iam_user (id, display_name, role_code, region_code, enabled, created_at)
select 'officer-a', 'A 区一线人员', 'OFFICER', 'district-a', true, current_timestamp
where not exists (select 1 from iam_user where id = 'officer-a');
insert into iam_user (id, display_name, role_code, region_code, enabled, created_at)
select 'window-a', 'A 区窗口人员', 'WINDOW', 'district-a', true, current_timestamp
where not exists (select 1 from iam_user where id = 'window-a');
insert into iam_user (id, display_name, role_code, region_code, enabled, created_at)
select 'manager-a', 'A 区管理人员', 'MANAGER', 'district-a', true, current_timestamp
where not exists (select 1 from iam_user where id = 'manager-a');
insert into iam_user (id, display_name, role_code, region_code, enabled, created_at)
select 'legal-user', '法制审核员', 'LEGAL', 'city', true, current_timestamp
where not exists (select 1 from iam_user where id = 'legal-user');
insert into iam_user (id, display_name, role_code, region_code, enabled, created_at)
select 'auditor-user', '审计人员', 'AUDITOR', 'city', true, current_timestamp
where not exists (select 1 from iam_user where id = 'auditor-user');
insert into iam_user (id, display_name, role_code, region_code, enabled, created_at)
select 'rate-limit-user', '限流测试用户', 'OFFICER', 'district-a', true, current_timestamp
where not exists (select 1 from iam_user where id = 'rate-limit-user');
