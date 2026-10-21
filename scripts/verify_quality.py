#!/usr/bin/env python3
"""检查文件输入边界、任务隔离与资料删除后的检索结果。"""

import argparse
import json
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlsplit
from urllib.request import Request, urlopen

from verify_baseline import request_json


def multipart(file_name, content):
    boundary = "staranaly-" + uuid.uuid4().hex
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{file_name}"\r\n'
        "Content-Type: application/octet-stream\r\n\r\n"
    ).encode("utf-8") + content + f"\r\n--{boundary}--\r\n".encode("utf-8")
    return body, f"multipart/form-data; boundary={boundary}"


def upload(base_url, task_id, resource, file_name, content):
    body, content_type = multipart(file_name, content)
    path = f"/api/tasks/{task_id}/{resource}"
    request = Request(base_url + path, data=body, method="POST",
                      headers={"Content-Type": content_type})
    try:
        with urlopen(request, timeout=30) as response:
            if response.status != 201:
                raise RuntimeError(f"POST {path} 返回 HTTP {response.status}，预期 201")
            return json.load(response)
    except HTTPError as error:
        raise RuntimeError(f"POST {path} 返回 HTTP {error.code}，预期 201") from error
    except (URLError, TimeoutError) as error:
        raise RuntimeError(f"无法连接后端：POST {path}") from error


def expect_error(base_url, method, path, status, data=None, content_type=None):
    headers = {"Content-Type": content_type} if content_type else {}
    request = Request(base_url + path, data=data, method=method, headers=headers)
    try:
        with urlopen(request, timeout=30) as response:
            raise RuntimeError(f"{method} {path} 意外返回 HTTP {response.status}，预期 {status}")
    except HTTPError as error:
        if error.code != status:
            raise RuntimeError(f"{method} {path} 返回 HTTP {error.code}，预期 {status}") from error
        try:
            problem = json.load(error)
        except (UnicodeError, json.JSONDecodeError) as cause:
            raise RuntimeError(f"{method} {path} 错误响应不是 JSON") from cause
        if not isinstance(problem.get("detail"), str) or not problem["detail"].strip():
            raise RuntimeError(f"{method} {path} 未返回可读的错误说明")
    except (URLError, TimeoutError) as error:
        raise RuntimeError(f"无法连接后端：{method} {path}") from error


def expect_upload_error(base_url, task_id, resource, file_name, content, status=400):
    body, content_type = multipart(file_name, content)
    expect_error(base_url, "POST", f"/api/tasks/{task_id}/{resource}", status, body, content_type)


def verify(base_url, csv_fixture, document_fixture):
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    owner = request_json(base_url, "POST", "/api/tasks", {
        "title": f"边界检查归属任务 {stamp}", "description": "第 7 周质量回归样例。",
    }, expected_status=201)["id"]
    other = request_json(base_url, "POST", "/api/tasks", {
        "title": f"边界检查其他任务 {stamp}", "description": "验证任务间文件不可混用。",
    }, expected_status=201)["id"]

    expect_upload_error(base_url, owner, "files", "wrong.md", csv_fixture.read_bytes())
    expect_upload_error(base_url, owner, "files", "bad.csv", b"\xc3\x28")
    expect_upload_error(base_url, owner, "files", "duplicate.csv", b"amount,amount\n1,2\n")
    expect_upload_error(base_url, owner, "documents", "bad.md", b"\xc3\x28")
    expect_upload_error(base_url, owner, "documents", "nul.md", b"valid\x00invalid")
    expect_upload_error(base_url, owner, "documents", "large.md", b"a" * (1024 * 1024 + 1))
    print("[通过] 错误扩展名、编码、重复表头、NUL 字符与超大资料均被拒绝")

    csv_file = upload(base_url, owner, "files", csv_fixture.name, csv_fixture.read_bytes())
    document = upload(base_url, owner, "documents", document_fixture.name,
                      document_fixture.read_bytes())
    file_id = csv_file["id"]
    document_id = document["id"]
    expect_error(base_url, "GET", f"/api/tasks/{other}/files/{file_id}", 404)
    expect_error(base_url, "DELETE", f"/api/tasks/{other}/documents/{document_id}", 404)
    if request_json(base_url, "GET", f"/api/tasks/{other}/documents/search?query={quote('退货期限')}")["hits"]:
        raise RuntimeError("其他任务检索到了归属任务的资料")
    print("[通过] 跨任务 CSV 读取与资料删除被拒绝，跨任务检索无命中")

    expect_error(base_url, "GET", f"/api/tasks/{owner}/documents/search?query={quote('退')}", 400)
    expect_error(base_url, "GET", f"/api/tasks/{owner}/documents/search?query={quote('退货')}&limit=6", 400)
    print("[通过] 无效检索词与 limit 越界返回明确错误")

    path = f"/api/tasks/{owner}/documents/{document_id}"
    try:
        with urlopen(Request(base_url + path, method="DELETE"), timeout=10) as response:
            if response.status != 204:
                raise RuntimeError(f"删除资料返回 HTTP {response.status}，预期 204")
    except (HTTPError, URLError, TimeoutError) as error:
        raise RuntimeError("无法删除质量检查资料") from error
    result = request_json(base_url, "GET",
                          f"/api/tasks/{owner}/documents/search?query={quote('退货期限')}")
    if result["hits"]:
        raise RuntimeError("删除后仍检索到资料")
    print("[通过] 删除资料后不再命中")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-base-url", default="http://localhost:8080")
    root = Path(__file__).resolve().parents[1]
    parser.add_argument("--csv-fixture", type=Path, default=root / "fixtures/monthly-sales.csv")
    parser.add_argument("--document-fixture", type=Path, default=root / "fixtures/knowledge-demo.md")
    args = parser.parse_args()
    if not args.csv_fixture.is_file() or not args.document_fixture.is_file():
        parser.error("固定样例文件不存在")
    base_url = args.api_base_url.rstrip("/")
    try:
        parsed = urlsplit(base_url)
    except ValueError:
        parser.error("--api-base-url 不是有效地址")
    if (parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username
            or parsed.password or parsed.query or parsed.fragment):
        parser.error("--api-base-url 必须是完整的 HTTP(S) 地址")
    try:
        verify(base_url, args.csv_fixture, args.document_fixture)
    except (RuntimeError, KeyError, TypeError, ValueError, AttributeError, IndexError) as error:
        print(f"[失败] {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
