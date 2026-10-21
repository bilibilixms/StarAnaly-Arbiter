#!/usr/bin/env python3
"""在测试环境中验证模型、只读工具与步骤记录的完整往返。"""

import argparse
import sys
import time
from datetime import datetime, timezone
from urllib.parse import urlsplit

from verify_baseline import request_json


def verify(base_url):
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    task = request_json(base_url, "POST", "/api/tasks", {
        "title": f"工具闭环验证 {stamp}",
        "description": "验证只读整数加法工具、模型结果回传与步骤记录。",
    }, expected_status=201)
    task_id = task["id"]
    run = request_json(base_url, "POST", f"/api/tasks/{task_id}/runs", {
        "content": "请调用 sum_two_numbers 计算 a=2、b=3，最终回答中写出阿拉伯数字 5。",
    }, expected_status=202)
    run_id = run["id"]
    print(f"[通过] 创建任务 #{task_id} 与执行 #{run_id}")

    deadline = time.monotonic() + 140
    while True:
        run = request_json(base_url, "GET", f"/api/tasks/{task_id}/runs/{run_id}")
        if run.get("status") in ("SUCCEEDED", "FAILED"):
            break
        if time.monotonic() >= deadline:
            raise RuntimeError("等待工具执行完成超过 140 秒")
        time.sleep(2)
    if run["status"] != "SUCCEEDED":
        raise RuntimeError(f"执行失败：{run.get('lastError') or '原因未知'}")

    steps = request_json(base_url, "GET", f"/api/tasks/{task_id}/runs/{run_id}/steps")
    kinds = [step.get("kind") for step in steps]
    if (len(steps) < 5 or kinds[0] != "MODEL_CALL"
            or kinds[-1] != "FINAL_ANSWER"
            or "TOOL_CALL" not in kinds or "TOOL_RESULT" not in kinds
            or kinds.index("TOOL_CALL") > kinds.index("TOOL_RESULT")
            or any(step.get("status") != "SUCCEEDED" for step in steps)
            or [step.get("stepNumber") for step in steps] != list(range(1, len(steps) + 1))):
        raise RuntimeError(f"工具步骤的顺序或状态不正确：{kinds}")
    tool_results = [step for step in steps if step.get("kind") == "TOOL_RESULT"]
    if not any(step.get("toolName") == "sum_two_numbers"
               and step.get("outputSummary") == "计算结果：5" for step in tool_results):
        raise RuntimeError("步骤中未记录整数加法结果 5")
    print("[通过] 模型调用、工具请求、工具结果及最终回答步骤已保存")

    messages = request_json(base_url, "GET", f"/api/tasks/{task_id}/messages")
    if (not isinstance(messages, list) or len(messages) != 2
            or [item.get("role") for item in messages] != ["USER", "ASSISTANT"]
            or "5" not in messages[-1].get("content", "")):
        raise RuntimeError("模型最终回答未包含工具计算结果 5")
    print("[通过] 工具结果已回传模型并形成最终回答")


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
