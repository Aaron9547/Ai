#!/bin/bash
set -euo pipefail

resolve_rmq_home() {
  for d in /home/rocketmq/rocketmq-* /opt/rocketmq-*; do
    if [ -d "$d" ] && [ -f "$d/bin/mqbroker" ]; then
      echo "$d"
      return 0
    fi
  done
  echo "[rocketmq-broker] ERROR: RocketMQ home not found" >&2
  exit 1
}

RMQ_HOME="$(resolve_rmq_home)"
CONF="${BROKER_CONF:-${RMQ_HOME}/conf/broker.conf}"
if [ ! -f "${CONF}" ]; then
  echo "[rocketmq-broker] ERROR: broker conf not found: ${CONF}" >&2
  exit 1
fi
NAMESRV="${NAMESRV_ADDR:-rocketmq-namesrv:9876}"
NS_HOST="${NAMESRV%:*}"
NS_PORT="${NAMESRV#*:}"

mkdir -p /home/rocketmq/store /home/rocketmq/logs
chown -R rocketmq:rocketmq /home/rocketmq/store /home/rocketmq/logs 2>/dev/null \
  || chown -R 3000:3000 /home/rocketmq/store /home/rocketmq/logs

echo "[rocketmq-broker] waiting for namesrv ${NAMESRV} ..."
ready=0
for _ in $(seq 1 60); do
  if (echo > "/dev/tcp/${NS_HOST}/${NS_PORT}") 2>/dev/null; then
    ready=1
    break
  fi
  sleep 2
done
if [ "${ready}" -ne 1 ]; then
  echo "[rocketmq-broker] ERROR: namesrv not reachable after 120s" >&2
  exit 1
fi

echo "[rocketmq-broker] starting (${CONF})"
cd "${RMQ_HOME}/bin"
if command -v runuser >/dev/null 2>&1; then
  exec runuser -u rocketmq -- ./mqbroker -n "${NAMESRV}" -c "${CONF}"
fi
exec su -s /bin/sh rocketmq -c "./mqbroker -n '${NAMESRV}' -c '${CONF}'"
