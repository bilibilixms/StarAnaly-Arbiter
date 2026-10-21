#!/usr/bin/env python3
"""依次运行最终验收的 CSV、资料问答和质量回归脚本。"""

import argparse
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlsplit


CHECKS = (
    ("CSV 分析", "verify_csv.py"),
    ("资料问答", "verify_knowledge.py"),
    ("质量回归", "verify_quality.py"),
)


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

    scripts_dir = Path(__file__).resolve().parent
    for label, script_name in CHECKS:
        print(f"[开始] {label}", flush=True)
        result = subprocess.run(
            [sys.executable, str(scripts_dir / script_name), "--api-base-url", base_url],
            check=False,
        )
        if result.returncode != 0:
            print(f"[失败] {label}，退出码 {result.returncode}", file=sys.stderr)
            return result.returncode
        print(f"[完成] {label}", flush=True)
    print("[通过] 最终 API 验收脚本全部完成")
    return 0


if __name__ == "__main__":
    sys.exit(main())
