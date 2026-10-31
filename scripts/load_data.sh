#!/usr/bin/env bash
# Загрузка ~1M анкет из test_data/people.v2.csv в таблицу users через COPY.
# Формат CSV: "Фамилия Имя,YYYY-MM-DD,Город"
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-15432}"
DB_NAME="${DB_NAME:-socialnetwork}"
DB_USER="${DB_USER:-sn_user}"
export PGPASSWORD="${DB_PASSWORD:-sn_password}"

CSV_PATH="${1:-test_data/people.v2.csv}"

psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 <<SQL
DROP TABLE IF EXISTS staging_people;
CREATE TABLE staging_people (name text, birthdate text, city text);
\copy staging_people FROM '${CSV_PATH}' WITH (FORMAT csv)

INSERT INTO users (id, first_name, second_name, birthdate, biography, city, gender, password_hash)
SELECT
    gen_random_uuid(),
    split_part(name, ' ', 2)                          AS first_name,
    split_part(name, ' ', 1)                          AS second_name,
    birthdate::date,
    NULL,
    city,
    CASE WHEN random() < 0.5 THEN 'male' ELSE 'female' END,
    md5(random()::text)
FROM staging_people;

DROP TABLE staging_people;

SELECT count(*) AS users_total FROM users;
SELECT count(*) AS search_check FROM users
WHERE first_name LIKE 'Ал%' AND second_name LIKE 'См%';
SQL
