#!/usr/bin/env bash
# Server one-shot: build images on this host + docker compose up
# Prereqs: git clone, JDK 25, Docker, docker compose v2
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$SCRIPT_DIR"

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Created deploy/.env — edit cluster MySQL/Redis/Milvus/MinIO, then run again."
  exit 1
fi

echo "=== Build images (local) ==="
bash "$REPO_ROOT/scripts/docker-build-images.sh"

echo "=== Start stack ==="
docker compose up -d

echo ""
echo "Done."
echo "  User:  http://<host>:${USER_WEB_PORT:-5173}/default/chat"
echo "  Admin: http://<host>:${ADMIN_WEB_PORT:-5174}/"
echo "  Run db/mysql/schema_v1.sql on cluster MySQL if not done yet."
echo ""
docker compose ps
