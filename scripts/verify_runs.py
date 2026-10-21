#!/usr/bin/env python3
"""在测试环境中核对异步执行记录、步骤和任务最终状态。"""

import argparse
import sys
import time
from datetime import datetime, timezone
from urllib.parse import urlsplit

from verify_baseline import request_json


def verify(base_url):
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    task = request_json(base_url, "POST", "/api/tasks", {
        "title": f"执行记录验证 {stamp}",
        "description": "验证异步执行与步骤持久化。",
    }, expected_status=201)
    task_id = task["id"]

    run = request_json(base_url, "POST", f"/api/tasks/{task_id}/runs", {
        "content": "请用一句中文确认执行记录功能可用。",
    }, expected_status=202)
    run_id = run["id"]
    if run.get("taskId") != task_id or run.get("status") != "RUNNING":
        raise RuntimeError("新执行记录的任务编号或初始状态不正确")
    print(f"[通过] 创建任务 #{task_id} 与执行 #{run_id}")

    deadline = time.monotonic() + 120
    while True:
        run = request_json(base_url, "GET", f"/api/tasks/{task_id}/runs/{run_id}")
        if run.get("status") in ("SUCCEEDED", "FAILED"):
            break
        if time.monotonic() >= deadline:
            raise RuntimeError("等待执行完成超过 120 秒")
        time.sleep(2)
    if run["status"] != "SUCCEEDED":
        raise RuntimeError(f"执行失败：{run.get('lastError') or '原因未知'}")
    if not run.get("finishedAt"):
        raise RuntimeError("执行完成后缺少结束时间")
    print("[通过] 执行状态转为 SUCCEEDED")

    steps = request_json(base_url, "GET", f"/api/tasks/{task_id}/runs/{run_id}/steps")
    if ([step.get("kind") for step in steps] != ["MODEL_CALL", "FINAL_ANSWER"]
            or [step.get("status") for step in steps] != ["SUCCEEDED", "SUCCEEDED"]
            or [step.get("stepNumber") for step in steps] != [1, 2]):
        raise RuntimeError("执行步骤的顺序或状态不正确")
    print("[通过] 模型调用与最终回复步骤已保存")

    page = request_json(base_url, "GET", f"/api/tasks/{task_id}/runs?page=0&size=20&sort=id,desc")
    if not any(item.get("id") == run_id for item in page.get("content", [])):
        raise RuntimeError("执行列表中找不到刚创建的记录")
    messages = request_json(base_url, "GET", f"/api/tasks/{task_id}/messages")
    final_task = request_json(base_url, "GET", f"/api/tasks/{task_id}")
    if ([item.get("role") for item in messages] != ["USER", "ASSISTANT"]
            or final_task.get("status") != "SUCCEEDED"):
        raise RuntimeError("执行结果未同步到任务状态与对话记录")
    print("[通过] 执行列表、任务状态与对话记录一致")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-base-url", default="http://localhost:8080")
    args = parser.parse_args()
    base_url = args.api_base_url.rstrip("/")
    try:
        parsed = urlsplit(base_url)
    except ValueError:
        parser.error("--api-base-url 不是有效地址")
    if (parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username
            or parsed.password or parsed.query or parsed.fragment):
        parser.error("--api-base-url 必须是完整的 HTTP(S) 地址")
    try:
        verify(base_url)
    except (RuntimeError, KeyError, TypeError, AttributeError) as error:
        print(f"[失败] {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
