alter table plan_step add column if not exists task_code varchar(64);
alter table plan_step add column if not exists task_type varchar(64);
alter table plan_step add column if not exists mandatory boolean default false;
alter table plan_step add column if not exists result_ref varchar(128);

update plan_step
set task_code = case step_order
    when 1 then 'question_analysis'
    when 2 then 'data_query_prepare'
    when 3 then 'data_query_execute'
    when 4 then 'answer_compose'
    else concat('task_', step_order)
end
where task_code is null;

update plan_step
set task_type = case step_order
    when 1 then 'QUESTION_ANALYSIS'
    when 2 then 'DATA_QUERY_PREPARE'
    when 3 then 'DATA_QUERY_EXECUTE'
    when 4 then 'ANSWER_COMPOSE'
    else 'ANSWER_COMPOSE'
end
where task_type is null;

update plan_step
set mandatory = case
    when task_type in ('DATA_QUERY_PREPARE', 'DATA_QUERY_EXECUTE', 'ANSWER_COMPOSE') then true
    else false
end
where mandatory is null;
