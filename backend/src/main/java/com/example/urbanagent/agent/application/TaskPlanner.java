package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.AgentPlanGraph;
import com.example.urbanagent.agent.application.dto.AgentTask;
import com.example.urbanagent.agent.application.dto.ExtractedSlot;
import com.example.urbanagent.agent.application.dto.IntentType;
import com.example.urbanagent.agent.application.dto.ParsedQuestion;
import com.example.urbanagent.agent.application.dto.SlotType;
import com.example.urbanagent.agent.application.dto.TaskType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TaskPlanner {

    public AgentPlanGraph buildGraph(ParsedQuestion parsedQuestion) {
        List<AgentTask> tasks = new ArrayList<>();
        int order = 1;
        tasks.add(new AgentTask(
                "question_analysis",
                TaskType.QUESTION_ANALYSIS,
                "问题解析",
                buildAnalysisGoal(parsedQuestion),
                true,
                List.of()
        ));

        List<Integer> answerDependencies = new ArrayList<>();
        // 数据查询任务
        if (parsedQuestion.hasMandatoryDataIntent()) {
            int prepareOrder = ++order;
            tasks.add(new AgentTask(
                    "data_query_prepare",
                    TaskType.DATA_QUERY_PREPARE,
                    "数据查询准备",
                    "识别指标、维度、区域与时间范围，并生成候选只读查询",
                    true,
                    List.of(1)
            ));
            int executeOrder = ++order;
            TaskType executeType = determineDataExecuteType(parsedQuestion);
            tasks.add(new AgentTask(
                    "data_query_execute",
                    executeType,
                    "数据查询执行",
                    "执行通过校验和权限改写的只读查询，生成结果摘要",
                    true,
                    List.of(prepareOrder)
            ));
            answerDependencies.add(executeOrder);

            // 图表生成任务
            if (needsChartGeneration(parsedQuestion)) {
                int chartOrder = ++order;
                tasks.add(new AgentTask(
                        "chart_generation",
                        TaskType.CHART_GENERATION,
                        "图表生成",
                        "根据查询结果生成柱状图、折线图、饼图等可视化",
                        false,
                        List.of(executeOrder)
                ));
                answerDependencies.add(chartOrder);
            }
        }

        // 知识检索任务
        if (parsedQuestion.requiresCitation()) {
            addKnowledgeTasks(parsedQuestion, tasks, answerDependencies, order);
        }

        boolean needsAnswerCompose = parsedQuestion.hasMandatoryDataIntent()
                || parsedQuestion.requiresCitation()
                || parsedQuestion.slotsOf(SlotType.OUTPUT_FORMAT).stream()
                .anyMatch(slot -> "analysis".equals(slot.normalizedValue())
                        || "comparison".equals(slot.normalizedValue())
                        || "trend".equals(slot.normalizedValue())
                        || "ranking".equals(slot.normalizedValue()));
        if (needsAnswerCompose) {
            if (answerDependencies.isEmpty()) {
                answerDependencies.add(1);
            }
            int answerOrder = ++order;
            tasks.add(new AgentTask(
                    "answer_compose",
                    TaskType.ANSWER_COMPOSE,
                    "答案生成",
                    "汇总任务结果并生成结构化分析结论",
                    true,
                    List.copyOf(answerDependencies)
            ));

            // 风险复核任务
            if (parsedQuestion.hasIntent(IntentType.LEGAL_ADVICE) || parsedQuestion.hasIntent(IntentType.BUSINESS_CONSULTATION)) {
                int riskOrder = ++order;
                tasks.add(new AgentTask(
                        "risk_review",
                        TaskType.RISK_REVIEW,
                        "风险复核",
                        "评估执法认定、处罚建议和敏感信息的风险等级",
                        false,
                        List.of(answerOrder)
                ));
                answerDependencies.add(riskOrder);
            }

            // 闸门检查任务
            int guardrailOrder = ++order;
            tasks.add(new AgentTask(
                    "guardrail_check",
                    TaskType.GUARDRAIL_CHECK,
                    "安全闸门",
                    "验证数据声明来源、政策法规依据和权限边界",
                    true,
                    List.of(answerOrder)
            ));
        }

        return new AgentPlanGraph(parsedQuestion, List.copyOf(tasks));
    }

    private TaskType determineDataExecuteType(ParsedQuestion parsedQuestion) {
        // 根据数据意图类型选择任务类型
        if (parsedQuestion.hasIntent(IntentType.BUSINESS_DATA_QUERY)) {
            return TaskType.BUSINESS_RECORD_QUERY;
        }
        return TaskType.METRIC_QUERY;
    }

    private boolean needsChartGeneration(ParsedQuestion parsedQuestion) {
        return parsedQuestion.slotsOf(SlotType.OUTPUT_FORMAT).stream()
                .map(ExtractedSlot::normalizedValue)
                .anyMatch(value -> "ranking".equals(value)
                        || "trend".equals(value)
                        || "distribution".equals(value)
                        || "comparison".equals(value));
    }

    private void addKnowledgeTasks(ParsedQuestion parsedQuestion, List<AgentTask> tasks,
                                   List<Integer> answerDependencies, int currentOrder) {
        int knowledgeOrder = ++currentOrder;

        if (parsedQuestion.hasIntent(IntentType.POLICY_INTERPRETATION)) {
            tasks.add(new AgentTask(
                    "policy_search",
                    TaskType.POLICY_SEARCH,
                    "政策检索",
                    "检索专项方案、考核要求、治理政策等文件",
                    true,
                    List.of(1)
            ));
            answerDependencies.add(++currentOrder);
        }

        if (parsedQuestion.hasIntent(IntentType.LEGAL_ADVICE)) {
            tasks.add(new AgentTask(
                    "law_search",
                    TaskType.LAW_SEARCH,
                    "法规检索",
                    "检索违法认定、处罚依据、裁量基准等法规条款",
                    true,
                    List.of(1)
            ));
            answerDependencies.add(++currentOrder);
        }

        if (parsedQuestion.hasIntent(IntentType.BUSINESS_CONSULTATION)) {
            tasks.add(new AgentTask(
                    "business_rule_search",
                    TaskType.BUSINESS_RULE_SEARCH,
                    "业务规则检索",
                    "检索处置流程、归口部门、责任单位、升级规则",
                    true,
                    List.of(1)
            ));
            answerDependencies.add(++currentOrder);
        }

        // 通用知识检索兜底
        if (!parsedQuestion.hasIntent(IntentType.POLICY_INTERPRETATION)
                && !parsedQuestion.hasIntent(IntentType.LEGAL_ADVICE)
                && !parsedQuestion.hasIntent(IntentType.BUSINESS_CONSULTATION)) {
            tasks.add(new AgentTask(
                    "knowledge_retrieve",
                    TaskType.KNOWLEDGE_RETRIEVE,
                    "知识检索",
                    "检索与问题相关的政策、法规或业务依据",
                    false,
                    List.of(1)
            ));
            answerDependencies.add(++currentOrder);
        }
    }

    private String buildAnalysisGoal(ParsedQuestion parsedQuestion) {
        StringBuilder builder = new StringBuilder("识别用户问题的意图、场景和关键槽位");
        if (!parsedQuestion.intents().isEmpty()) {
            builder.append("；意图数=").append(parsedQuestion.intents().size());
        }
        if (!parsedQuestion.scenes().isEmpty()) {
            builder.append("；场景=").append(parsedQuestion.scenes());
        }
        return builder.toString();
    }
}
