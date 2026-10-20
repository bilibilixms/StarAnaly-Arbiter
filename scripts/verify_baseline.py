#!/usr/bin/env python3
"""在已启动的测试环境中验证当前任务与对话 API。"""

import argparse
import json
import sys
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


def request_json(base_url, method, path, payload=None, expected_status=200, timeout=10):
    body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = Request(
        base_url + path,
        data=body,
        method=method,
        headers={"Content-Type": "application/json"} if body is not None else {},
    )
    try:
        with urlopen(request, timeout=timeout) as response:
            if response.status != expected_status:
                raise RuntimeError(f"{method} {path} 返回 HTTP {response.status}，预期 {expected_status}")
            return json.load(response)
    except HTTPError as error:
        raise RuntimeError(f"{method} {path} 返回 HTTP {error.code}") from error
    except (URLError, TimeoutError) as error:
        raise RuntimeError(f"无法连接后端：{method} {path}") from error
    except (UnicodeError, json.JSONDecodeError) as error:
        raise RuntimeError(f"{method} {path} 返回的不是有效 JSON") from error


def verify(base_url):
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    task = request_json(
        base_url,
        "POST",
        "/api/tasks",
        {"title": f"基线验证 {stamp}", "description": "用于验证任务、数据库迁移和模型对话。"},
        expected_status=201,
    )
    if not isinstance(task, dict):
        raise RuntimeError("创建任务未返回 JSON 对象")
    task_id = task.get("id")
    if not isinstance(task_id, int) or task_id <= 0 or task.get("status") != "PENDING":
        raise RuntimeError("创建任务返回的编号或初始状态不正确")
    print(f"[通过] 创建任务 #{task_id}，初始状态 PENDING")

    found = request_json(base_url, "GET", f"/api/tasks/{task_id}")
    if not isinstance(found, dict) or found.get("id") != task_id:
        raise RuntimeError("任务详情与创建结果不一致")
    page = request_json(base_url, "GET", "/api/tasks?page=0&size=20&sort=id,desc")
    items = page.get("content") if isinstance(page, dict) else None
    if not isinstance(items, list) or not any(
        isinstance(item, dict) and item.get("id") == task_id for item in items
    ):
        raise RuntimeError("任务列表中找不到刚创建的任务")
    print("[通过] 查询任务详情与分页列表")

    reply = request_json(
        base_url,
        "POST",
        f"/api/tasks/{task_id}/messages",
        {"content": "请用一句中文确认模型对话可用。"},
        timeout=110,
    )
    if (not isinstance(reply, dict) or reply.get("role") != "ASSISTANT"
            or not isinstance(reply.get("content"), str) or not reply["content"].strip()):
        raise RuntimeError("模型未返回有效的文本回复")
    messages = request_json(base_url, "GET", f"/api/tasks/{task_id}/messages")
    if (not isinstance(messages, list) or not all(isinstance(item, dict) for item in messages)
            or [item.get("role") for item in messages] != ["USER", "ASSISTANT"]):
        raise RuntimeError("对话消息未按预期保存")
    finished = request_json(base_url, "GET", f"/api/tasks/{task_id}")
    if not isinstance(finished, dict) or finished.get("status") != "SUCCEEDED":
        raise RuntimeError("任务最终状态不是 SUCCEEDED")
    print("[通过] 模型回复、消息持久化与 SUCCEEDED 状态")
    print(f"基线验证完成；生成的任务编号：{task_id}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-base-url", default="http://localhost:8080", help="后端地址，默认 http://localhost:8080")
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
    except RuntimeError as error:
        print(f"[失败] {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
