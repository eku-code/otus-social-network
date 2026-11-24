#!/bin/bash
# Entrypoint слейва: при первом старте выполняет pg_basebackup с мастера
# (включая -R: standby.signal + primary_conninfo), затем запускает postgres.
set -e

REPLICATION_USER="${REPLICATION_USER:-replicator}"
REPLICATION_PASSWORD="${REPLICATION_PASSWORD:-rep_password}"
MASTER_HOST="${MASTER_HOST:-db-master}"
APPLICATION_NAME="${APPLICATION_NAME:-$(hostname)}"

if [ ! -s "$PGDATA/PG_VERSION" ]; then
    echo "Waiting for master $MASTER_HOST..."
    until PGPASSWORD="$REPLICATION_PASSWORD" pg_isready -h "$MASTER_HOST" -U "$REPLICATION_USER"; do
        sleep 1
    done

    echo "Running pg_basebackup from $MASTER_HOST..."
    rm -rf "$PGDATA"/*
    PGPASSWORD="$REPLICATION_PASSWORD" pg_basebackup \
        -h "$MASTER_HOST" -U "$REPLICATION_USER" \
        -D "$PGDATA" -Fp -Xs -P -R

    # hot_standby и application_name (перекрывает значения из primary_conninfo от -R).
    cat >> "$PGDATA/postgresql.conf" <<EOF

# added by replica-entrypoint
hot_standby = on
EOF
    # Разрешаем репликацию с любых хостов: после промоушена этот узел сам станет
    # мастером и должен принимать basebackup/подключения walsender'ов от других реплик.
    cat >> "$PGDATA/pg_hba.conf" <<EOF
host replication ${REPLICATION_USER} all scram-sha-256
EOF
    cat >> "$PGDATA/postgresql.auto.conf" <<EOF
primary_conninfo = 'host=$MASTER_HOST port=5432 user=$REPLICATION_USER password=$REPLICATION_PASSWORD application_name=$APPLICATION_NAME'
EOF
    chown -R postgres:postgres "$PGDATA"
fi

exec /usr/local/bin/docker-entrypoint.sh postgres
