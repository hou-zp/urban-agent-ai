\set ON_ERROR_STOP on

select format('create role %I login password %L', :'app_user', :'app_password')
where not exists (select 1 from pg_roles where rolname = :'app_user')
\gexec

select format('alter role %I login password %L', :'app_user', :'app_password')
\gexec

select format('create database %I owner %I', :'app_db', :'app_user')
where not exists (select 1 from pg_database where datname = :'app_db')
\gexec

\connect :app_db

do
$$
begin
    create extension if not exists vector;
exception
    when others then
        raise notice 'pgvector extension is not installed, skip vector extension initialization';
end
$$;

select format('grant connect, temporary on database %I to %I', :'app_db', :'app_user')
\gexec

select format('grant usage, create on schema public to %I', :'app_user')
\gexec

select format('grant select, insert, update, delete on all tables in schema public to %I', :'app_user')
\gexec

select format('grant usage, select on all sequences in schema public to %I', :'app_user')
\gexec

select format('alter default privileges in schema public grant select, insert, update, delete on tables to %I', :'app_user')
\gexec

select format('alter default privileges in schema public grant usage, select on sequences to %I', :'app_user')
\gexec
