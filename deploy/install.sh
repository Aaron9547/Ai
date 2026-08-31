#!/usr/bin/env bash
# 本地构建镜像 + 启动前后端（基础设施须已运行，网络 super-agent-infra）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== Build images ==="
bash "$REPO_ROOT/scripts/docker-build-images.sh"

echo "=== Start backend + frontends ==="
cd "$SCRIPT_DIR"
docker compose up -d

echo ""
echo "Done."
echo "  User:  http://<host>:${USER_WEB_PORT:-5173}/default/chat"
echo "  Admin: http://<host>:${ADMIN_WEB_PORT:-5174}/"
docker compose ps
