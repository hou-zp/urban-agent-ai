# 验收测试集

本目录用于承载 `QA-401` 验收测试集。

当前提供 5 份 CSV 题库：

- `policy_regulation_questions.csv`：200 条，覆盖政策咨询和法律法规咨询。
- `business_questions.csv`：80 条，覆盖业务流程咨询。
- `data_query_questions.csv`：80 条，覆盖智能问数。
- `analysis_questions.csv`：20 条，覆盖综合分析。
- `security_questions.csv`：50 条，覆盖权限、安全和高风险拦截。

辅助脚本：

- `generate_acceptance_set.py`：按固定模板重新生成验收题库。
- `validate_acceptance_set.py`：校验文件结构、必填字段、重复 case_id 和最低数量。

校验命令：

```bash
python3 qa/acceptance/validate_acceptance_set.py
```

使用要求：

- 业务侧问题占比不低于 70%。
- 正式验收前冻结版本，避免边测边改题。
- 题目、预期行为、标准依据、判定角色必须完整填写。
