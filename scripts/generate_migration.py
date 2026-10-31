#!/usr/bin/env python3
"""Генерация Liquibase-миграции с 1M INSERT'ов анкет из test_data/people.v2.csv.

Оптимизации скорости:
- многострочные INSERT ... VALUES (пачки по N строк) вместо отдельных INSERT:
  в разы меньше парсинга и round-trip'ов;
- INSERT ... SELECT ... FROM (VALUES): id, password_hash генерируются на стороне
  БД (gen_random_uuid(), md5(random())), не передаются по сети;
- SET LOCAL synchronous_commit = off: без fsync на каждый коммит
  (действует только на транзакцию этой миграции).

Формат CSV: "Фамилия Имя,YYYY-MM-DD,Город".
"""
import argparse

BATCH_SIZE = 2000


def esc(s: str) -> str:
    return s.replace("'", "''")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--csv", default="test_data/people.v2.csv")
    ap.add_argument("--out", default="src/main/resources/db/changelog/migrations/002-load-test-data.sql")
    ap.add_argument("--batch-size", type=int, default=BATCH_SIZE)
    args = ap.parse_args()

    rows = []
    with open(args.csv, "rb") as f:
        for raw in f:
            try:
                line = raw.decode("utf-8").rstrip("\n")
            except UnicodeDecodeError:
                # Последняя строка файла обрезана посреди UTF-8 символа — пропускаем.
                continue
            if not line:
                continue
            name, birthdate, city = line.split(",")
            surname, firstname = name.split(" ", 1)
            rows.append((firstname, surname, birthdate, city))

    with open(args.out, "w", encoding="utf-8") as out:
        out.write(
            "-- Загрузка анкет из test_data/people.v2.csv (многострочные INSERT пачками).\n"
            "--changeset otus:002-load-test-data\n"
            "--preconditions onFail:MARK_RAN onError:MARK_RAN\n"
            "--precondition-sql-check expectedResult:0 SELECT count(*) FROM users\n"
            "SET LOCAL synchronous_commit = off;\n"
        )
        for i in range(0, len(rows), args.batch_size):
            batch = rows[i:i + args.batch_size]
            out.write(
                "INSERT INTO users (id, first_name, second_name, birthdate, city, gender, password_hash)\n"
                "SELECT gen_random_uuid(), first_name, second_name, birthdate::date, city, gender, md5(random()::text)\n"
                "FROM (VALUES\n"
            )
            # Пол детерминированно чередуется, чтобы распределение было равномерным.
            lines = []
            for j, (fn, sn, bd, city) in enumerate(batch):
                gender = "male" if (i + j) % 2 == 0 else "female"
                lines.append(f"('{esc(fn)}','{esc(sn)}','{bd}','{esc(city)}','{gender}')")
            out.write(",\n".join(lines))
            out.write("\n) AS t(first_name, second_name, birthdate, city, gender);\n")

    print(f"rows: {len(rows)}, batches: {(len(rows) + args.batch_size - 1) // args.batch_size}, out: {args.out}")


if __name__ == "__main__":
    main()
