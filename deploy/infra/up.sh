#!/usr/bin/env bash
# Start infrastructure only
set -euo pipefail
cd "$(dirname "$0")"
docker compose up -d
docker compose ps
