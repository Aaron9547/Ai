#!/usr/bin/env bash
# Full deploy: infra + build images + app stack
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== Infrastructure (deploy/infra) ==="
cd "$SCRIPT_DIR/infra"
docker compose up -d

echo "=== Build application images ==="
bash "$REPO_ROOT/scripts/docker-build-images.sh"

echo "=== Application stack (deploy) ==="
cd "$SCRIPT_DIR"
docker compose up -d

echo ""
echo "Done."
echo "  User:  http://<host>:${USER_WEB_PORT:-5173}/default/chat"
echo "  Admin: http://<host>:${ADMIN_WEB_PORT:-5174}/"
echo ""
docker compose ps
