package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.PlanStepFailureCode;
import com.example.urbanagent.agent.application.dto.PlanStepHandleCode;
import org.springframework.stereotype.Component;

@Component
public class PlanStepFailureTemplateResolver {

    public PlanStepFailureTemplate resolve(PlanStepFailureCode errorCode) {
        if (errorCode == null) {
            return new PlanStepFailureTemplate(
                    "EXECUTION_FAILED",
                    "当前步骤执行失败",
                    "RETRY_CURRENT",
                    "可直接重试",
                    PlanStepHandleCode.RETRY_STEP,
                    false,
                    "当前步骤执行失败，请根据建议处理后重试。"
            );
        }

        return switch (errorCode) {
            case SQL_PERMISSION_DENIED -> new PlanStepFailureTemplate(
                    "EXECUTION_FAILED",
                    "当前步骤需要更高的数据权限",
                    "RETRY_CURRENT",
                    "调整权限后重试",
                    PlanStepHandleCode.SWITCH_ROLE,
                    false,
                    "当前查询命中了无访问权限字段，请切换到有权限的角色，或缩小查询范围后重试。"
            );
            case PLAN_QUERY_PREVIEW_MISSING -> new PlanStepFailureTemplate(
                    "DEPENDENCY_BLOCKED",
                    "需先重建查询准备",
                    "REBUILD_DEPENDENCIES",
                    "重建查询准备",
                    PlanStepHandleCode.REBUILD_QUERY_PREVIEW,
                    true,
                    "查询准备产物已失效，请先重跑数据查询准备步骤。"
            );
            case PLAN_QUERY_RESULT_MISSING -> new PlanStepFailureTemplate(
                    "DEPENDENCY_BLOCKED",
                    "需先重建数据结果",
                    "REBUILD_DEPENDENCIES",
                    "重建数据结果",
                    PlanStepHandleCode.REBUILD_QUERY_RESULT,
                    true,
                    "答案生成依赖的数据结果已失效，请先重跑数据查询执行步骤。"
            );
            case PLAN_KNOWLEDGE_RESULT_MISSING -> new PlanStepFailureTemplate(
                    "DEPENDENCY_BLOCKED",
                    "需先重建依据检索",
                    "REBUILD_DEPENDENCIES",
                    "重建依据检索",
                    PlanStepHandleCode.REBUILD_KNOWLEDGE,
                    true,
                    "依据检索产物已缺失，请先重跑依据检索步骤。"
            );
            case PLAN_DEPENDENCY_INCOMPLETE -> new PlanStepFailureTemplate(
                    "DEPENDENCY_BLOCKED",
                    "需先补跑前置步骤",
                    "REBUILD_DEPENDENCIES",
                    "补跑前置步骤",
                    PlanStepHandleCode.REBUILD_DEPENDENCIES,
                    true,
                    "前置步骤尚未恢复完成，请先补跑依赖步骤后再继续当前步骤。"
            );
            case PLAN_DEPENDENCY_BLOCKED -> new PlanStepFailureTemplate(
                    "DEPENDENCY_BLOCKED",
                    "前置依赖仍未就绪",
                    "REBUILD_DEPENDENCIES",
                    "先补前置步骤",
                    PlanStepHandleCode.REBUILD_DEPENDENCIES,
                    true,
                    "当前步骤依赖的前置结果尚未就绪，请先恢复依赖步骤。"
            );
            case PLAN_STEP_EXECUTION_FAILED -> new PlanStepFailureTemplate(
                    "EXECUTION_FAILED",
                    "当前步骤执行失败",
                    "RETRY_CURRENT",
                    "可直接重试",
                    PlanStepHandleCode.RETRY_STEP,
                    false,
                    "当前步骤执行失败，请根据建议处理后重试。"
            );
        };
    }

    public record PlanStepFailureTemplate(
            String category,
            String headline,
            String action,
            String actionLabel,
            PlanStepHandleCode handleCode,
            boolean dependencyBlocked,
            String defaultReason
    ) {
    }
}
