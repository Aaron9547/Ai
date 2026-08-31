#!/bin/bash
set -euo pipefail

if [ ! -f /schema/schema_v1.sql ]; then
  echo "schema file missing: /schema/schema_v1.sql" >&2
  exit 1
fi

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE:-ai}" < /schema/schema_v1.sql

if [ -f /schema/gw_catalog.sql ]; then
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE:-ai}" < /schema/gw_catalog.sql
fi
