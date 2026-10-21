#!/usr/bin/env python3
"""验证 CSV 上传、预览、筛选、按月汇总及模型工具闭环。"""

import argparse
import json
import sys
import time
import uuid
from decimal import Decimal
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

from verify_baseline import request_json


def upload(base_url, task_id, path):
    boundary = "staranaly-" + uuid.uuid4().hex
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{path.name}"\r\n'
        "Content-Type: text/csv\r\n\r\n"
    ).encode("utf-8") + path.read_bytes() + f"\r\n--{boundary}--\r\n".encode("utf-8")
    request = Request(
        f"{base_url}/api/tasks/{task_id}/files",
        data=body,
        method="POST",
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
    )
    try:
        with urlopen(request, timeout=30) as response:
            if response.status != 201:
                raise RuntimeError(f"CSV 上传返回 HTTP {response.status}，预期 201")
            return json.load(response)
    except HTTPError as error:
        raise RuntimeError(f"CSV 上传返回 HTTP {error.code}") from error
    except (URLError, TimeoutError) as error:
        raise RuntimeError("无法连接后端上传 CSV") from error


def verify(base_url, fixture):
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    task = request_json(base_url, "POST", "/api/tasks", {
        "title": f"CSV 按月汇总验证 {stamp}",
        "description": "使用上传的销售数据汇总各月 amount。",
    }, expected_status=201)
    task_id = task["id"]
    file = upload(base_url, task_id, fixture)
    file_id = file["id"]
    if file.get("rowCount") != 4 or file.get("headers") != ["date", "category", "amount"]:
        raise RuntimeError("CSV 上传后的列名或数据行数不正确")
    print(f"[通过] 上传 CSV 文件 #{file_id}，4 行数据")

    preview = request_json(base_url, "GET", f"/api/tasks/{task_id}/files/{file_id}")
    rows = request_json(base_url, "GET",
                        f"/api/tasks/{task_id}/files/{file_id}/rows?filterColumn=category&filterValue=A")
    if len(preview.get("rows", [])) != 4 or len(rows) != 2:
        raise RuntimeError("CSV 预览或筛选结果不正确")
    print("[通过] CSV 预览与 category=A 筛选")

    aggregate = request_json(base_url, "GET",
                             f"/api/tasks/{task_id}/files/{file_id}/aggregate"
                             "?groupBy=date&sumColumn=amount&byMonth=true")
    groups = {group["key"]: Decimal(str(group["sum"])) for group in aggregate.get("groups", [])}
    if (groups != {"2026-01": Decimal(300), "2026-02": Decimal(200)}
            or Decimal(str(aggregate.get("total", -1))) != Decimal(500)):
        raise RuntimeError(f"CSV 按月汇总与固定样例不一致：{groups}")
    print("[通过] 按月汇总：2026-01=300，2026-02=200，总计 500")

    run = request_json(base_url, "POST", f"/api/tasks/{task_id}/runs", {
        "content": f"请务必调用 summarize_csv 工具，fileId={file_id}，groupBy=date，"
                   "sumColumn=amount，byMonth=true。最终回答请写出 2026-01、2026-02 和总计。",
    }, expected_status=202)
    run_id = run["id"]
    deadline = time.monotonic() + 140
    while True:
        current = request_json(base_url, "GET", f"/api/tasks/{task_id}/runs/{run_id}")
        if current.get("status") in ("SUCCEEDED", "FAILED"):
            break
        if time.monotonic() >= deadline:
            raise RuntimeError("等待 CSV 工具执行超过 140 秒")
        time.sleep(2)
    if current["status"] != "SUCCEEDED":
        raise RuntimeError(f"CSV 工具执行失败：{current.get('lastError') or '原因未知'}")
    steps = request_json(base_url, "GET", f"/api/tasks/{task_id}/runs/{run_id}/steps")
    if not any(step.get("kind") == "TOOL_RESULT" and step.get("toolName") == "summarize_csv"
               and "总计 500" in step.get("outputSummary", "") for step in steps):
        raise RuntimeError("执行步骤未记录 CSV 工具结果")
    analyses = request_json(base_url, "GET", f"/api/tasks/{task_id}/runs/{run_id}/analysis")
    if len(analyses) != 1 or Decimal(str(analyses[0]["result"]["total"])) != Decimal(500):
        raise RuntimeError("结构化分析结果未保存")
    print("[通过] 模型调用 CSV 工具，步骤与结构化结果均已保存")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-base-url", default="http://localhost:8080")
    parser.add_argument("--fixture", type=Path,
                        default=Path(__file__).resolve().parents[1] / "fixtures/monthly-sales.csv")
    args = parser.parse_args()
    if not args.fixture.is_file():
        parser.error("CSV 样例文件不存在")
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
