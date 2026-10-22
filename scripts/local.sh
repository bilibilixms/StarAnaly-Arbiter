#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose=(docker compose --project-directory "$project_dir" -f "$project_dir/docker-compose.yml" -f "$project_dir/docker-compose.local.yml")

wait_for_app() {
    ready=false
    for ((attempt = 0; attempt < 60; attempt++)); do
      if curl --fail --silent --max-time 2 --output /dev/null http://127.0.0.1:5173/ \
          && curl --fail --silent --max-time 2 --output /dev/null 'http://127.0.0.1:5173/api/tasks?page=0&size=1'; then
        ready=true
        break
      fi
      sleep 2
    done
    if [[ "$ready" != true ]]; then
      echo "服务未在 2 分钟内就绪，请运行 bash scripts/local.sh logs 查看日志。" >&2
      exit 1
    fi
    echo "星析已启动：http://localhost:5173"
}

case "${1:-}" in
  up)
    "${compose[@]}" up -d mysql backend frontend
    wait_for_app
    ;;
  restart)
    "${compose[@]}" up -d --no-deps --force-recreate backend frontend
    wait_for_app
    ;;
  down)
    "${compose[@]}" down
    ;;
  status)
    "${compose[@]}" ps
    ;;
  logs)
    "${compose[@]}" logs -f "${@:2}"
    ;;
  test)
    "${compose[@]}" run --rm --no-deps backend mvn -B -ntp test
    "${compose[@]}" run --rm --no-deps frontend sh -lc 'npm ci --no-audit --no-fund && npm run build'
    docker run --rm -v "$project_dir/browser-extension:/extension:ro" node:24-bookworm-slim \
      sh -lc 'node --test /extension/xiaohongshu/test/*.test.cjs'
    ;;
  *)
    echo "用法: scripts/local.sh {up|restart|down|status|logs [服务名]|test}" >&2
    exit 2
    ;;
esac
