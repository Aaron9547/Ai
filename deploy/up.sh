#!/usr/bin/env bash
# Pull pre-built images and start (skip local build)
set -euo pipefail
cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Created .env — edit and run again."
  exit 1
fi

docker compose pull
docker compose up -d

echo ""
echo "Done."
echo "  User:  http://<host>:${USER_WEB_PORT:-5173}/default/chat"
echo "  Admin: http://<host>:${ADMIN_WEB_PORT:-5174}/"
docker compose ps
