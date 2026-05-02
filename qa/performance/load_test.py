#!/usr/bin/env python3

from __future__ import annotations

import argparse
import concurrent.futures
import json
import math
import statistics
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path


@dataclass
class LoadResult:
    name: str
    total: int
    success: int
    failed: int
    p50_ms: float
    p95_ms: float
    max_ms: float
    threshold_ms: int

    @property
    def passed(self) -> bool:
        return self.failed == 0 and self.p95_ms <= self.threshold_ms


def http_json(method: str, url: str, payload: dict | None = None, headers: dict[str, str] | None = None) -> tuple[int, dict]:
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Accept", "application/json")
    if payload is not None:
        request.add_header("Content-Type", "application/json")
    if headers:
        for key, value in headers.items():
            request.add_header(key, value)
    with urllib.request.urlopen(request, timeout=30) as response:
        body = response.read().decode("utf-8")
        return response.status, json.loads(body)


def timed_call(func):
    started = time.perf_counter()
    func()
    return (time.perf_counter() - started) * 1000


def percentile(values: list[float], ratio: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, math.ceil(len(ordered) * ratio) - 1)
    return ordered[index]


def benchmark(name: str, total: int, concurrency: int, threshold_ms: int, task_factory) -> LoadResult:
    durations: list[float] = []
    failures = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(task_factory, index) for index in range(total)]
        for future in concurrent.futures.as_completed(futures):
            try:
                durations.append(future.result())
            except Exception:
                failures += 1
    success = len(durations)
    return LoadResult(
        name=name,
        total=total,
        success=success,
        failed=failures,
        p50_ms=statistics.median(durations) if durations else 0.0,
        p95_ms=percentile(durations, 0.95),
        max_ms=max(durations) if durations else 0.0,
        threshold_ms=threshold_ms,
    )


def create_chat_session(base_url: str, user_id: str) -> str:
    status, payload = http_json(
        "POST",
        f"{base_url}/api/v1/agent/sessions",
        {"title": f"压测会话-{user_id}"},
        {"X-User-Id": user_id},
    )
    if status != 200 or payload.get("code") != 0:
        raise RuntimeError(f"create session failed: {payload}")
    return payload["data"]["id"]


def warm_up(base_url: str) -> None:
    status, payload = http_json("POST", f"{base_url}/api/v1/data/catalog/sync")
    if status != 200 or payload.get("code") != 0:
        raise RuntimeError(f"sync catalog failed: {payload}")


def run_chat_test(base_url: str, total: int, concurrency: int) -> LoadResult:
    session_ids = [create_chat_session(base_url, f"chat-user-{index}") for index in range(total)]

    def task(index: int) -> float:
        session_id = session_ids[index]
        headers = {
            "X-User-Id": f"chat-user-{index}",
            "X-User-Role": "OFFICER",
            "X-User-Region": "district-a",
        }
        return timed_call(
            lambda: http_json(
                "POST",
                f"{base_url}/api/v1/agent/sessions/{session_id}/messages",
                {"content": f"请说明当前系统能力，压测请求 {index}"},
                headers,
            )
        )

    return benchmark("普通咨询", total, concurrency, 8000, task)


def run_query_preview_test(base_url: str, total: int, concurrency: int) -> LoadResult:
    def task(index: int) -> float:
        headers = {
            "X-User-Id": f"query-user-{index}",
            "X-User-Role": "OFFICER",
            "X-User-Region": "district-a",
        }
        return timed_call(
            lambda: http_json(
                "POST",
                f"{base_url}/api/v1/data/query/preview",
                {"question": "查询本周各街道投诉数量排行"},
                headers,
            )
        )

    return benchmark("简单问数预览", total, concurrency, 10000, task)


def render_report(base_url: str, chat_result: LoadResult, query_result: LoadResult, report_file: Path) -> None:
    report = f"""# 性能压测报告

## 1. 执行信息

- 日期：{time.strftime("%Y-%m-%d %H:%M:%S")}
- 目标地址：`{base_url}`
- 压测脚本：`qa/performance/load_test.py`

## 2. 场景结果

| 场景 | 总请求数 | 成功 | 失败 | P50(ms) | P95(ms) | Max(ms) | 阈值(ms) | 结果 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| {chat_result.name} | {chat_result.total} | {chat_result.success} | {chat_result.failed} | {chat_result.p50_ms:.2f} | {chat_result.p95_ms:.2f} | {chat_result.max_ms:.2f} | {chat_result.threshold_ms} | {"通过" if chat_result.passed else "未通过"} |
| {query_result.name} | {query_result.total} | {query_result.success} | {query_result.failed} | {query_result.p50_ms:.2f} | {query_result.p95_ms:.2f} | {query_result.max_ms:.2f} | {query_result.threshold_ms} | {"通过" if query_result.passed else "未通过"} |

## 3. 判定

- 普通咨询目标：`P95 <= 8000 ms`
- 简单问数目标：`P95 <= 10000 ms`

综合结论：{"通过" if chat_result.passed and query_result.passed else "未通过"}
"""
    report_file.write_text(report, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:19081")
    parser.add_argument("--chat-requests", type=int, default=20)
    parser.add_argument("--query-requests", type=int, default=20)
    parser.add_argument("--concurrency", type=int, default=5)
    parser.add_argument("--report-file", default="docs/11-performance-test-report.md")
    args = parser.parse_args()

    try:
        warm_up(args.base_url)
        chat_result = run_chat_test(args.base_url, args.chat_requests, args.concurrency)
        query_result = run_query_preview_test(args.base_url, args.query_requests, args.concurrency)
        report_file = Path(args.report_file)
        report_file.parent.mkdir(parents=True, exist_ok=True)
        render_report(args.base_url, chat_result, query_result, report_file)
        print(report_file)
        print(json.dumps({
            "chat": chat_result.__dict__,
            "query": query_result.__dict__,
        }, ensure_ascii=False))
        return 0 if chat_result.passed and query_result.passed else 1
    except urllib.error.HTTPError as exc:
        print(f"http error: {exc.code} {exc.reason}")
        return 2
    except Exception as exc:
        print(f"load test failed: {exc}")
        return 3


if __name__ == "__main__":
    raise SystemExit(main())
