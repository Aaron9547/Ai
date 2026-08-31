#!/usr/bin/env bash
# Build ai-backend / ai-user-web / ai-admin-web images locally.
# Image names: ${IMAGE_PREFIX}/ai-*:${IMAGE_TAG} (from deploy/.env or defaults).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

if [[ -f deploy/.env ]]; then
  set -a
  # shellcheck disable=SC1091
  source deploy/.env
  set +a
fi

IMAGE_PREFIX="${IMAGE_PREFIX:-ai-platform}"
IMAGE_TAG="${IMAGE_TAG:-0.1.258-SNAPSHOT}"

if ! docker info >/dev/null 2>&1; then
  echo "Docker is not available."
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "JDK not found. Install JDK 25 and set JAVA_HOME."
  exit 1
fi

echo "Image prefix: ${IMAGE_PREFIX}"
echo "Image tag:    ${IMAGE_TAG}"

echo ">>> ai-backend"
./mvnw clean package -Dmaven.test.skip=true \
  -Ddocker.exec.skip=false \
  -Ddocker.image.registry="${IMAGE_PREFIX}" \
  exec:exec@docker-build

echo ">>> ai-user-web"
docker build -f web/user-web/Dockerfile \
  -t "${IMAGE_PREFIX}/ai-user-web:${IMAGE_TAG}" \
  ./web

echo ">>> ai-admin-web"
docker build -f web/admin-web/Dockerfile \
  -t "${IMAGE_PREFIX}/ai-admin-web:${IMAGE_TAG}" \
  ./web

echo "Done:"
echo "  ${IMAGE_PREFIX}/ai-backend:${IMAGE_TAG}"
echo "  ${IMAGE_PREFIX}/ai-user-web:${IMAGE_TAG}"
echo "  ${IMAGE_PREFIX}/ai-admin-web:${IMAGE_TAG}"
