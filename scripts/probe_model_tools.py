#!/usr/bin/env python3
"""探测兼容 Chat Completions 的模型是否支持工具调用及结果回传。"""

import json
import os
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


TOOL = {
    "type": "function",
    "function": {
        "name": "sum_two_numbers",
        "description": "Return the sum of two integers.",
        "parameters": {
            "type": "object",
            "properties": {"a": {"type": "integer"}, "b": {"type": "integer"}},
            "required": ["a", "b"],
            "additionalProperties": False,
        },
    },
}


class NoRedirectHandler(HTTPRedirectHandler):
    def redirect_request(self, request, file_pointer, code, message, headers, new_url):
        return None


OPENER = build_opener(NoRedirectHandler())


def complete(endpoint, api_key, model, messages, **options):
    payload = {"model": model, "messages": messages, **options}
    request = Request(
        endpoint,
        data=json.dumps(payload).encode("utf-8"),
        method="POST",
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
    )
    try:
        with OPENER.open(request, timeout=90) as response:
            result = json.load(response)
    except HTTPError as error:
        raise RuntimeError(f"模型服务返回 HTTP {error.code}；请检查模型名称、额度和工具调用参数") from error
    except (URLError, TimeoutError) as error:
        raise RuntimeError("无法连接模型服务；请检查 MODEL_BASE_URL 和网络") from error
    except (UnicodeError, json.JSONDecodeError) as error:
        raise RuntimeError("模型服务未返回有效 JSON") from error
    try:
        message = result["choices"][0]["message"]
    except (KeyError, IndexError, TypeError) as error:
        raise RuntimeError("模型回复缺少 choices[0].message") from error
    if not isinstance(message, dict):
        raise RuntimeError("模型回复中的 message 不是对象")
    return message


def verify():
    api_key = (os.getenv("MODEL_API_KEY") or os.getenv("DEEPSEEK_API_KEY") or "").strip()
    model = os.getenv("MODEL_NAME", "deepseek-flash").strip()
    base_url = os.getenv("MODEL_BASE_URL", "https://api.deepseek.com").strip().rstrip("/")
    if not api_key:
        raise RuntimeError("请先在当前终端配置 MODEL_API_KEY 或 DEEPSEEK_API_KEY")
    if not model:
        raise RuntimeError("MODEL_NAME 不能为空")
    try:
        parsed = urlsplit(base_url)
    except ValueError as error:
        raise RuntimeError("MODEL_BASE_URL 不是有效地址") from error
    if (parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username
            or parsed.password or parsed.query or parsed.fragment):
        raise RuntimeError("MODEL_BASE_URL 必须是完整的 HTTP(S) 基础地址")
    endpoint = base_url + "/chat/completions"
    # DeepSeek 默认开启思考模式；指定工具的 tool_choice 需要非思考模式。
    provider_options = {"thinking": {"type": "disabled"}} if model.startswith("deepseek-") else {}

    user_message = {"role": "user", "content": "Call sum_two_numbers with a=2 and b=3."}
    tool_request = complete(
        endpoint,
        api_key,
        model,
        [user_message],
        tools=[TOOL],
        tool_choice={"type": "function", "function": {"name": "sum_two_numbers"}},
        **provider_options,
    )
    calls = tool_request.get("tool_calls")
    if not isinstance(calls, list) or not calls:
        raise RuntimeError("模型未返回 tool_calls；当前模型或服务可能不支持工具调用")

    tool_results = []
    safe_calls = []
    for call in calls:
        try:
            if call["type"] != "function" or call["function"]["name"] != "sum_two_numbers":
                raise RuntimeError("模型请求了未声明的工具")
            arguments = json.loads(call["function"]["arguments"])
            if arguments != {"a": 2, "b": 3}:
                raise RuntimeError("模型工具参数与探测样例不符")
            call_id = call["id"]
            if not isinstance(call_id, str) or not call_id:
                raise RuntimeError("模型工具调用缺少有效 ID")
        except (KeyError, TypeError, json.JSONDecodeError) as error:
            raise RuntimeError("模型返回的工具调用格式不符合预期") from error
        safe_calls.append({
            "id": call_id,
            "type": "function",
            "function": {"name": "sum_two_numbers", "arguments": call["function"]["arguments"]},
        })
        tool_results.append({"role": "tool", "tool_call_id": call_id, "content": "5"})
    print(f"[通过] 模型返回 {len(calls)} 个有效工具调用")

    assistant_call = {"role": "assistant", "content": tool_request.get("content"), "tool_calls": safe_calls}
    answer = complete(
        endpoint,
        api_key,
        model,
        [user_message, assistant_call, *tool_results],
        tools=[TOOL],
        tool_choice="none",
        **provider_options,
    )
    if not isinstance(answer.get("content"), str) or not answer["content"].strip():
        raise RuntimeError("提交工具结果后，模型未返回文本回复")
    print("[通过] 工具结果回传后得到文本回复")
    print("模型工具调用探测完成；脚本未执行任何外部工具或写入操作")


if __name__ == "__main__":
    try:
        verify()
    except RuntimeError as error:
        print(f"[失败] {error}", file=sys.stderr)
        sys.exit(1)
