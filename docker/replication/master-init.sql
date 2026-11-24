-- Создание пользователя для потоковой репликации.
CREATE ROLE ${REPLICATION_USER} WITH REPLICATION LOGIN PASSWORD '${REPLICATION_PASSWORD}';

-- Разрешаем репликацию с любых хостов docker-сети.
ALTER SYSTEM SET listen_addresses = '*';

-- Записи в pg_hba.conf (файл находится внутри PGDATA).
CREATE TABLE pg_hba_extension (line text);
