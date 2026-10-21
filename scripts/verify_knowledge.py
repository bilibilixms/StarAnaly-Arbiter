#!/usr/bin/env python3
"""验证资料上传、检索、模型工具、来源引用和删除。"""

import argparse
import json
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlsplit
from urllib.request import Request, urlopen

from verify_baseline import request_json


def upload(base_url, task_id, path):
    boundary = "staranaly-" + uuid.uuid4().hex
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{path.name}"\r\n'
        "Content-Type: text/markdown; charset=utf-8\r\n\r\n"
    ).encode("utf-8") + path.read_bytes() + f"\r\n--{boundary}--\r\n".encode("utf-8")
    request = Request(
        f"{base_url}/api/tasks/{task_id}/documents",
        data=body,
        method="POST",
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
    )
    try:
        with urlopen(request, timeout=30) as response:
            if response.status != 201:
                raise RuntimeError(f"资料上传返回 HTTP {response.status}，预期 201")
            return json.load(response)
    except HTTPError as error:
        raise RuntimeError(f"资料上传返回 HTTP {error.code}") from error
    except (URLError, TimeoutError) as error:
        raise RuntimeError("无法连接后端上传资料") from error


def delete(base_url, task_id, document_id):
    path = f"/api/tasks/{task_id}/documents/{document_id}"
    try:
        with urlopen(Request(base_url + path, method="DELETE"), timeout=10) as response:
            if response.status != 204:
                raise RuntimeError(f"删除资料返回 HTTP {response.status}，预期 204")
    except HTTPError as error:
        raise RuntimeError(f"删除资料返回 HTTP {error.code}") from error
    except (URLError, TimeoutError) as error:
        raise RuntimeError("无法连接后端删除资料") from error


def search(base_url, task_id, query):
    return request_json(base_url, "GET",
                        f"/api/tasks/{task_id}/documents/search?query={quote(query)}")


def verify(base_url, fixture):
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    task = request_json(base_url, "POST", "/api/tasks", {
        "title": f"资料检索验证 {stamp}",
        "description": "根据演示资料回答普通订单退货期限。",
    }, expected_status=201)
    task_id = task["id"]
    document = upload(base_url, task_id, fixture)
    document_id = document["id"]
    if (document.get("name") != fixture.name or document.get("chunkCount", 0) < 1):
        raise RuntimeError("资料上传后的元数据不正确")
    listed = request_json(base_url, "GET", f"/api/tasks/{task_id}/documents")
    if not any(item.get("id") == document_id for item in listed):
        raise RuntimeError("资料列表中找不到上传的文件")
    print(f"[通过] 上传并列出资料 #{document_id}")

    result = search(base_url, task_id, "普通订单何时可以申请退货")
    hits = result.get("hits", [])
    if not hits or not any(hit.get("documentId") == document_id
                           and hit.get("startLine") == 3
                           and "14 天" in hit.get("excerpt", "") for hit in hits):
        raise RuntimeError("检索未命中退货期限及原文行号")
    if search(base_url, task_id, "火星天气").get("hits"):
        raise RuntimeError("无关检索词产生了错误命中")
    print("[通过] 检索命中原文第 3 行，且无关问题无命中")

    run = request_json(base_url, "POST", f"/api/tasks/{task_id}/runs", {
        "content": "请先调用 search_knowledge 检索‘普通订单退货期限’，再回答普通订单何时可以申请退货。"
                   "回答中请写出资料名称、行号和阿拉伯数字形式的天数。",
    }, expected_status=202)
    run_id = run["id"]
    deadline = time.monotonic() + 140
    while True:
        current = request_json(base_url, "GET", f"/api/tasks/{task_id}/runs/{run_id}")
        if current.get("status") in ("SUCCEEDED", "FAILED"):
            break
        if time.monotonic() >= deadline:
            raise RuntimeError("等待资料检索工具执行超过 140 秒")
        time.sleep(2)
    if current["status"] != "SUCCEEDED":
        raise RuntimeError(f"资料检索工具执行失败：{current.get('lastError') or '原因未知'}")
    steps = request_json(base_url, "GET", f"/api/tasks/{task_id}/runs/{run_id}/steps")
    if not any(step.get("kind") == "TOOL_RESULT" and step.get("toolName") == "search_knowledge"
               for step in steps):
        raise RuntimeError("执行步骤未记录资料检索结果")
    citations = request_json(base_url, "GET", f"/api/tasks/{task_id}/runs/{run_id}/citations")
    if not any(citation.get("documentId") == document_id
               and citation.get("startLine") == 3
               and "14 天" in citation.get("excerpt", "") for citation in citations):
        raise RuntimeError("执行引用未保存资料来源与行号")
    messages = request_json(base_url, "GET", f"/api/tasks/{task_id}/messages")
    if not messages or messages[-1].get("role") != "ASSISTANT" or "14" not in messages[-1].get("content", ""):
        raise RuntimeError("最终回答未包含资料中的 14 天期限")
    print("[通过] 模型调用检索工具，回答与结构化来源引用均已保存")

    delete(base_url, task_id, document_id)
    if search(base_url, task_id, "普通订单何时可以申请退货").get("hits"):
        raise RuntimeError("删除资料后仍能检索到原文")
    if request_json(base_url, "GET", f"/api/tasks/{task_id}/runs/{run_id}/citations"):
        raise RuntimeError("删除资料后引用记录没有清理")
    print("[通过] 删除资料后检索与引用均已清理")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-base-url", default="http://localhost:8080")
    parser.add_argument("--fixture", type=Path,
                        default=Path(__file__).resolve().parents[1] / "fixtures/knowledge-demo.md")
    args = parser.parse_args()
    if not args.fixture.is_file():
        parser.error("知识检索样例文件不存在")
    base_url = args.api_base_url.rstrip("/")
    try:
        parsed = urlsplit(base_url)
    except ValueError:
        parser.error("--api-base-url 不是有效地址")
    if (parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username
            or parsed.password or parsed.query or parsed.fragment):
        parser.error("--api-base-url 必须是完整的 HTTP(S) 地址")
    try:
        verify(base_url, args.fixture)
    except (RuntimeError, KeyError, TypeError, ValueError, AttributeError, IndexError) as error:
        print(f"[失败] {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
