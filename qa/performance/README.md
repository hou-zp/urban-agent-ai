# 性能压测

本目录用于任务清单 `QA-403`。

当前提供：

- `load_test.py`：基于 Python 标准库的本地压测脚本。

默认压测两个场景：

1. 普通咨询。
2. 简单问数预览。

输出：

- 控制台 JSON 摘要。
- `docs/11-performance-test-report.md` 报告文件。
