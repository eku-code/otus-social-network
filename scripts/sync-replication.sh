#!/usr/bin/env bash
# Включение/выключение кворумной синхронной репликации на мастере.
# ANY 2 (slave1, slave2): коммит ждет подтверждения от любых 2 из 3 узлов.
# Использование: ./sync-replication.sh on|off [compose-файл]
set -euo pipefail

COMPOSE_FILE="${2:-docker-compose.replication.yml}"
MASTER_CONTAINER="social-network-db-master-1"

MODE="${1:-on}"
case "$MODE" in
  on)  VALUE="'ANY 2 (slave1, slave2)'";;
  off) VALUE="''";;
  *) echo "usage: $0 on|off [compose-file]"; exit 1;;
esac

echo "Setting synchronous_standby_names = $VALUE on master..."
docker exec "$MASTER_CONTAINER" psql -U "${POSTGRES_USER:-sn_user}" -d "${POSTGRES_DB:-socialnetwork}" \
  -c "ALTER SYSTEM SET synchronous_standby_names = $VALUE;" \
  -c "SELECT pg_reload_conf();"

echo "Current sync state:"
docker exec "$MASTER_CONTAINER" psql -U "${POSTGRES_USER:-sn_user}" -d "${POSTGRES_DB:-socialnetwork}" \
  -c "SELECT application_name, sync_state FROM pg_stat_replication;"
