#!/usr/bin/env bash
# Failover-эксперимент:
#   1) останавливает мастер (docker stop — аналог kill -9 для стенда),
#   2) промоутит самый свежий слейв (slave1) до мастера,
#   3) перестраивает slave2 как реплику нового мастера,
#   4) переключает приложение на нового мастера.
# Использование: ./failover.sh [compose-файл]
set -euo pipefail

COMPOSE_FILE="${1:-docker-compose.replication.yml}"
CF="-f $COMPOSE_FILE"
PROMOTE_SLAVE="${PROMOTE_SLAVE:-db-slave1}"   # промоутим самый свежий слейв
OTHER_SLAVE="${OTHER_SLAVE:-db-slave2}"

echo "==> 1. Stopping master (simulating crash)"
docker compose $CF stop db-master

echo "==> 2. Promoting $PROMOTE_SLAVE to master"
docker exec "social-network-${PROMOTE_SLAVE}-1" psql -U "${POSTGRES_USER:-sn_user}" -d "${POSTGRES_DB:-socialnetwork}" \
  -c "SELECT pg_promote();"
sleep 2

echo "==> 3. Rebuilding $OTHER_SLAVE as a replica of $PROMOTE_SLAVE"
docker compose $CF stop "$OTHER_SLAVE"
docker compose $CF rm -f "$OTHER_SLAVE"
VOLUME="$OTHER_SLAVE-data"
docker volume rm "social-network_${VOLUME}" 2>/dev/null || true
# SLAVE1_MASTER / SLAVE2_MASTER — переменные подстановки соответствующих сервисов в compose.
# --no-deps: не поднимать заново остановленный старый мастер (иначе split-brain).
SLAVE1_MASTER="$PROMOTE_SLAVE" SLAVE2_MASTER="$PROMOTE_SLAVE" docker compose $CF up -d --no-deps "$OTHER_SLAVE"

echo "==> 4. Repointing app to $PROMOTE_SLAVE (reads -> $OTHER_SLAVE)"
docker compose $CF stop app
APP_DB_HOST="$PROMOTE_SLAVE" \
  REPLICA_URLS="${FAILOVER_REPLICA_URLS:-jdbc:postgresql://${OTHER_SLAVE}:5432/${POSTGRES_DB:-socialnetwork}}" \
  docker compose $CF up -d --no-deps app

echo "==> Done. New topology:"
echo "    master: $PROMOTE_SLAVE"
echo "    replica: $OTHER_SLAVE (of $PROMOTE_SLAVE)"
docker compose $CF ps
