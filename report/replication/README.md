# ДЗ «Репликация» — отчёт

Цель: настроить потоковую репликацию PostgreSQL (1 мастер + 2 слейва), перевести запросы чтения
на слейвы, измерить эффект и провести эксперимент по потере/непотере транзакций при аварийной
остановке мастера (async vs кворумная синхронная репликация).

---

## 1. Топология и настройка репликации

Стенд: `docker-compose.replication.yml` (PostgreSQL 16):

| Узел | Контейнер | Порт | Роль |
|---|---|---|---|
| `db-master` | social-network-db-master-1 | 15432 | мастер |
| `db-slave1` | social-network-db-slave1-1 | 15433 | слейв 1 |
| `db-slave2` | social-network-db-slave2-1 | 15434 | слейв 2 |
| `app` | social-network-app-1 | 8080 | Spring Boot |

Как настроена репликация:

- **Мастер** (`docker/replication/master-init.sh` + `pg_hba.conf`):
  - создаётся роль `replicator` с правами `REPLICATION`;
  - `pg_hba.conf`: `host replication replicator all scram-sha-256` (репликация с любых хостов docker-сети);
  - параметры: `wal_level=replica`, `max_wal_senders=10`, `max_replication_slots=10`, `hot_standby=on`.
- **Слейвы** (`docker/replication/replica-entrypoint.sh`):
  - при первом старте выполняют `pg_basebackup -Fp -Xs -R` с мастера (физическая репликация,
    `standby.signal` + `primary_conninfo` пишутся автоматически);
  - включают `hot_standby=on` (чтение на слейве во время recovery);
  - в `pg_hba.conf` слейва добавляется строка для `replication` — после промоушена узел сам
    сможет принимать реплики.
- Потоковая (физическая) репликация подтверждается на мастере:

```sql
SELECT application_name, state, sync_state FROM pg_stat_replication;
--  slave1 | streaming | async
--  slave2 | streaming | async
```

Запуск стенда:

```bash
docker compose -f docker-compose.replication.yml up -d --build
./scripts/load_data.sh            # ~1M анкет из test_data/people.v2.csv
```

## 2. Маршрутизация запросов: чтение -> слейвы, запись -> мастер

Реализован рекомендованный вариант — `ReplicationRoutingDataSource` (без внешних прокси):

- `src/main/java/ru/otus/sn/config/ReplicationRoutingDataSource.java` — наследник
  `AbstractRoutingDataSource`; `determineCurrentLookupKey()` смотрит
  `TransactionSynchronizationManager.isCurrentTransactionReadOnly()`:
  - `readOnly = true` -> один из слейвов (round-robin);
  - иначе -> мастер.
- `DataSourceConfig`:
  - `masterDataSource` (HikariCP, `spring.datasource.*` / `DB_URL`);
  - `replicaDataSources` — пулы к слейвам из `REPLICA_URLS` (список JDBC URL через запятую);
  - поверх — `LazyConnectionDataSourceProxy`: физическое соединение открывается только к моменту
    первого SQL, когда readOnly-флаг транзакции уже известен — иначе роутинг бы «опаздывал».
- Разметка транзакций в `UserService`:
  - `getById`, `search` — `@Transactional(readOnly = true)` -> слейвы;
  - `register`, `login` — без readOnly -> мастер.

Проверить режимы: `REPLICA_URLS=""` (пустая переменная окружения) — все запросы на мастере
(базовый замер); по умолчанию — чтение со слейвов.

## 3. План нагрузочного тестирования (чтение)

Выбранные API: `GET /user/get/{id}` и `GET /user/search?first_name=&last_name=` (оба readOnly).
Инструмент — `scripts/read_load_test.py` (только stdlib, новый HTTP-connetion на запрос):

- профиль: `--endpoint mix` — чередование get/search 50/50; префиксы имён выбираются
  случайным образом из 100 комбинаций (селективность как в реальном поиске);
- уровни конкурентности: 1 / 10 / 100 потоков;
- длительность: 30 с на уровень + 5 с warmup (JIT, пулы соединений, кэш БД);
- метрики: throughput (req/s), latency min/mean/p50/p95/p99/max;
- распределение нагрузки по узлам: дельты `pg_stat_database.xact_commit` / `tup_returned`
  на каждом узле до/после прогона;
- данные: 999 930 анкет; LRU-кэш не прогревается заранее — равные условия для обоих режимов.

## 4. Результаты замеров

### 4.1 Производительность

| Конкурентность | Режим | RPS | p50, мс | p95, мс | p99, мс |
|---|---|---|---|---|---|
| 1 | master-only | 142.4 | 4.8 | 19.9 | 33.5 |
| 1 | master+2-slaves | **152.9 (+7%)** | 4.9 | **15.8 (-21%)** | **23.4 (-30%)** |
| 10 | master-only | **338.7** | 26.4 | **60.5** | **99.1** |
| 10 | master+2-slaves | 318.3 | 27.6 | 68.5 | 105.1 |
| 100 | master-only | 358.7 | 259.4 | 470.4 | 682.3 |
| 100 | master+2-slaves | **371.0 (+3%)** | **250.7** | **459.9** | 683.2 |

Графики: `read_throughput.png`, `read_latency_p95.png`.

Выводы по производительности:
- при низкой (1) и высокой (100) конкурентности чтение со слейвов выигрывает и в пропускной
  способности (+7% / +3%), и в хвостах латентности (p95 -21% / -2%);
- на уровне 10 результат сопоставим (-6% по RPS в пределах шума): узкое место стенда —
  клиент (новое TCP-соединение на запрос) и сам приложение, а не БД.

### 4.2 Доказательство перехода нагрузки на слейвы

Дельты `pg_stat_database` за одинаковые прогоны (~29.5K запросов, `node_stats.json`):

| Узел | master-only: xact / строк | master+2-slaves: xact / строк |
|---|---|---|
| db-master | 57 725 / **3 539 914** | 29 313 / **32 631** |
| db-slave1 | 54 / 902 | 14 778 / **1 980 050** |
| db-slave2 | 54 / 902 | 14 758 / **1 376 216** |

- в режиме master-only весь трафик чтения (3.5M строк) обслуживает мастер, слейвы простаивают;
- после включения роутинга тяжёлые чтения (миллионы строк) ушли на слейвы и распределены
  round-robin ровно 50/50 (14 778 / 14 758 транзакций);
- на мастере остались только однорядные транзакции проверки auth-токена (~1 строка на
  транзакцию — BearerTokenAuthFilter работает вне readOnly-транзакции; это осознанное
  решение: проверка только что выданного токена на слейве могла бы «не видеть» его из-за
  репликационного лага);
- записи (`/user/register`, `/login`) уходят на мастер — это видно по самому факту работы:
  слейвы read-only, любая запись туда завершилась бы ошибкой `cannot execute INSERT in a
  read-only transaction`.

## 5. Кворумная синхронная репликация

`scripts/sync-replication.sh on` на мастере:

```sql
ALTER SYSTEM SET synchronous_standby_names = 'ANY 2 (slave1, slave2)';
SELECT pg_reload_conf();
```

Коммит на мастере ждёт подтверждения flush от **любых 2 из списка** (master + 2 слейва = кластер
из 3 голосующих: потеря любого одного узла не блокирует запись и не теряет подтверждённые
транзакции).

```sql
SELECT application_name, sync_state FROM pg_stat_replication;
--  slave1 | quorum
--  slave2 | quorum
```

## 6. Эксперимент: потеря транзакций при аварийной остановке мастера

Сценарий (`scripts/write_load_test.py` + `scripts/transaction-loss-experiment.sh`):

1. нагрузка на запись `POST /user/register` (10 потоков), клиент считает успешные записи (2xx + валидный `user_id`);
2. в середине прогона слейв1 отключается от сети (`docker network disconnect`) — имитация
   отставшей реплики (типовая производственная ситуация);
3. через 7 с после этого мастер убивается: `docker kill --signal=KILL` (аналог `kill -9`);
4. нагрузка завершается; слейв промоутится (`SELECT pg_promote()`);
5. на промоутнутом узле проверяется, сколько из «успешно записанных» строк реально сохранилось.

### 6.1 Async-репликация, лага нет (первый прогон)

| Успешно записано (клиент) | Найдено на слейве после промоушена | Потеряно |
|---|---|---|
| 577 | 577 | **0** |

Локальная сеть — реплика успевала за низкой нагрузкой записи, окно потерь оказалось пустым.

### 6.2 Async-репликация, отставшая реплика (партиция)

| Успешно записано (клиент) | Найдено на промоутнутом slave1 | Потеряно |
|---|---|---|
| 849 | 432 | **417** |

Мастер подтвердил клиенту 849 коммитов, но 417 транзакций из 7-секундного окна партиции
не успели уйти в WAL-поток slave1 и погибли вместе с мастером. **Async-репликация не даёт
гарантий durability на реплике (RPO > 0).**

### 6.3 Кворумная sync-репликация `ANY 2`, тот же сценарий (партиция + kill -9)

| Успешно записано (клиент) | Найдено на промоутнутом slave2 | Потеряно |
|---|---|---|
| 405 | 405 | **0** |

Особенности:
- каждая подтверждённая клиенту транзакция гарантированно есть на мастере **и** на обоих
  слейвах (кворум 2 из 2 оставшихся), поэтому после гибели мастера терять нечего;
- во время партиции slave1 кворум собрать нельзя — коммиты на мастере **останавливаются и
  ждут** (клиент не получает 2xx). Заметен trade-off sync-репликации: durability ценой
  доступности записи (в async-прогоне за то же время клиент получил 849 «успехов», из которых
  49% потом потеряли; в sync — 405 реальных и ни одна не потеряна).

## 7. Failover: промоут свежего слейва и переключение

`scripts/failover.sh` (использовался в sync-эксперименте):

1. остановка старого мастера (уже убит `kill -9`);
2. выбор самого свежего слейва (slave2 — единственный, продолжавший получать WAL) и
   `SELECT pg_promote();` — он становится новым мастером (проверка: `pg_is_in_recovery() = f`);
3. slave1 перестраивается как реплика нового мастера: контейнер и волюм пересоздаются,
   `SLAVE1_MASTER=db-slave2` -> свежий `pg_basebackup` от нового мастера;
4. приложение переключается на нового мастера (`APP_DB_HOST=db-slave2`, чтения -> `REPLICA_URLS=db-slave1`);
5. старый мастер сознательно НЕ поднимается (иначе split-brain: два принимающих запись узла);
   `up -d` для реплики выполняется с `--no-deps`, чтобы compose не «помог» поднять зависимость db-master.

Итоговая топология после failover:

```sql
-- на db-slave2 (новый мастер):
SELECT application_name, state FROM pg_stat_replication;
--  slave1 | streaming
```

Непрерывность сервиса проверена сразу после переключения: `POST /user/register` -> 200
(запись на новый мастер), `GET /user/get/{id}` и `GET /user/search` -> 200 (чтение с
перестроенного слейва), новая анкета реплицируется на слейв.

## 8. Как воспроизвести

```bash
# 1. стенд: мастер + 2 слейва + приложение
docker compose -f docker-compose.replication.yml up -d --build
./scripts/load_data.sh                                   # 1M анкет

# 2. регистрируем пользователя и получаем токен (для read-теста)
curl -s -X POST :8080/user/register -H 'Content-Type: application/json' \
  -d '{"first_name":"Тест","second_name":"Тестов","birthdate":"1990-01-01","password":"secret"}'
curl -s -X POST :8080/login -H 'Content-Type: application/json' \
  -d '{"id":"<user_id>","password":"secret"}'

# 3. базовый замер (всё на мастере)
REPLICA_URLS="" docker compose -f docker-compose.replication.yml up -d --force-recreate --no-build app
python3 scripts/read_load_test.py --token <token> --user-id <user_id> \
  --endpoint mix --concurrency 100 --duration 30 --label master-only

# 4. замер с чтением со слейвов (режим по умолчанию)
docker compose -f docker-compose.replication.yml up -d --force-recreate --no-build app
python3 scripts/read_load_test.py --token <token> --user-id <user_id> \
  --endpoint mix --concurrency 100 --duration 30 --label master+2-slaves

# 5. эксперимент с потерей транзакций (async)
./scripts/transaction-loss-experiment.sh 20 10 off

# 6. кворумная синхронная репликация + эксперимент без потерь
./scripts/sync-replication.sh on
./scripts/transaction-loss-experiment.sh 20 10 on

# 7. полный failover (промоут свежего слейва, перестройка второго, переключение app)
PROMOTE_SLAVE=db-slave2 OTHER_SLAVE=db-slave1 ./scripts/failover.sh
```

Файлы результатов: `read_results.json`, `node_stats.json`, `txn_loss_async_no_lag.json`,
`txn_loss_async_partition.json`, `txn_loss_sync_quorum.json` (рядом с этим отчётом).

## 9. Выводы

- Потоковая репликация 1 мастер + 2 слейва настроена; запросы `/user/get/{id}` и
  `/user/search` переведены на слейвы через `@Transactional(readOnly = true)` +
  `ReplicationRoutingDataSource` (round-robin).
- Нагрузочное тестирование показало переход чтения на слейвы: мастер перестал сканировать
  строки (3.5M -> 0.03M строк за прогон), слейвы делят нагрузку 50/50; пропускная способность
  и хвосты латентности улучшились (до +7% RPS и -21% p95).
- При аварийной гибели мастера в async-режиме подтверждённые клиенту транзакции теряются,
  если промоутится отставшая реплика (417 из 849 в эксперименте); кворумная синхронная
  репликация `ANY 2` гарантирует отсутствие потерь (0 потеряно) ценой остановки коммитов
  при деградации кворума.
- Отработан полный failover: промоут свежего слейва, перестройка второй реплики через
  pg_basebackup, переключение приложения без потери непрерывности сервиса.
