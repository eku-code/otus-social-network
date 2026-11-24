#!/usr/bin/env bash
# Эксперимент по потере транзакций при аварийной остановке мастера.
#
# Схема:
#   1. (опционально) включает кворумную синхронную репликацию (ANY 2).
#   2. Запускает нагрузку на запись (POST /user/register), клиент считает успешные записи.
#   3. Через KILL_AT секунд после старта посылает SIGKILL мастеру.
#   4. Нагрузка завершается, промоутим самый свежий слейв (db-slave1).
#   5. Сверяем на новом мастере, сколько из "успешно записанных" строк реально сохранилось.
#
# Использование: ./transaction-loss-experiment.sh [duration_s] [kill_at_s] [sync: off|on]
set -euo pipefail

DURATION="${1:-20}"
KILL_AT="${2:-10}"
SYNC="${3:-off}"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.replication.yml}"
CF="-f $COMPOSE_FILE"
APP_HOST="${APP_HOST:-localhost}"
APP_PORT="${APP_PORT:-8080}"
MASTER_CONTAINER="social-network-db-master-1"
PROMOTE_SLAVE="${PROMOTE_SLAVE:-db-slave1}"
OUT="report/txn_loss.json"

if [ "$SYNC" = "on" ]; then
  echo "==> Enabling quorum synchronous replication (ANY 2)"
  ./scripts/sync-replication.sh on "$COMPOSE_FILE"
fi

echo "==> Starting write load: duration=${DURATION}s, master kill at +${KILL_AT}s"
python3 scripts/write_load_test.py --host "$APP_HOST" --port "$APP_PORT" \
  --concurrency 10 --duration "$DURATION" --kill-at "$KILL_AT" \
  --kill-cmd "docker kill --signal=KILL $MASTER_CONTAINER" \
  --label "sync=$SYNC" --out "$OUT"

echo "==> Promoting $PROMOTE_SLAVE"
docker exec "social-network-${PROMOTE_SLAVE}-1" \
  psql -U "${POSTGRES_USER:-sn_user}" -d "${POSTGRES_DB:-socialnetwork}" -c "SELECT pg_promote();"
sleep 3

echo "==> Verifying written rows on promoted slave ($PROMOTE_SLAVE)"
python3 - "$OUT" <<'PYEOF'
import json, subprocess, sys
data = json.load(open(sys.argv[1]))
ids = data["written_ids"]
res = data["result"]
print(f"Client counted successful writes: {res['successful_writes']}")
if not ids:
    print("No ids to verify"); sys.exit(0)
id_list = ",".join("'%s'" % i for i in ids)
sql = f"SELECT count(*) FROM users WHERE id IN ({id_list});"
out = subprocess.run(
    ["docker", "exec", "social-network-db-slave1-1", "psql", "-tA", "-U",
     "sn_user", "-d", "socialnetwork", "-c", sql],
    capture_output=True, text=True, check=True).stdout.strip()
present = int(out)
lost = res["successful_writes"] - present
print(f"Found on promoted slave: {present}")
print(f"LOST TRANSACTIONS: {lost}")
PYEOF

if [ "$SYNC" = "on" ]; then
  echo "==> Disabling quorum synchronous replication"
  ./scripts/sync-replication.sh off "$COMPOSE_FILE"
fi
