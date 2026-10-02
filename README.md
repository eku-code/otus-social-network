# Social Network — анкеты пользователей (OTUS Highload)

Монолитное приложение на **Spring Boot 3** (Java 17), БД — **PostgreSQL**, миграции — **Liquibase**.
Доступ к БД — `JdbcTemplate` с параметризованными запросами (без ORM).

Реализованные методы:
- `POST /login` — авторизация, возвращает токен
- `POST /user/register` — регистрация анкеты
- `GET /user/get/{id}` — получение анкеты (требуется `Authorization: Bearer <token>`)

Безопасность:
- **Spring Security** (stateless): `/login` и `/user/register` открыты, остальные методы требуют `Authorization: Bearer <token>`;
- токен проверяется фильтром `BearerTokenAuthFilter` по таблице `tokens`;
- все SQL-запросы — параметризованные (`JdbcTemplate` + `?`), конкатенация SQL отсутствует;
- пароли хранятся в виде bcrypt-хэшей (`BCryptPasswordEncoder`, cost 10), в открытом виде нигде не сохраняются и не логируются;
- токены — UUID v4, хранятся в таблице `tokens`.

Схема БД создаётся автоматически миграциями Liquibase при старте приложения.

### Как добавить новую миграцию

1. Создайте файл `src/main/resources/db/changelog/migrations/NNN-описание.sql` в формате Liquibase SQL:

```sql
--liquibase formatted sql

--changeset otus:NNN-описание
ALTER TABLE users ADD COLUMN phone VARCHAR(20);
```

2. Перезапустите приложение — новые (ещё не применённые) файлы подхватятся автоматически благодаря
`includeAll` в `db/changelog/db.changelog-master.xml` и выполнятся в алфавитном порядке имён.

Именование: порядковый префикс `NNN-` обязателен, он задаёт порядок выполнения
(например, `001-init-schema.sql`, `002-add-phones.sql`).

## Локальный запуск

### Вариант 1: всё в Docker (рекомендуется)

Требуется только Docker. Сборка приложения выполняется внутри Docker (multi-stage build):

```bash
docker compose build
docker compose up -d
```

Поднимаются два контейнера: `app` (http://localhost:8080) и `db` (PostgreSQL, наружу на `localhost:15432`).
Схема БД создаётся миграциями Liquibase автоматически при старте приложения.

### Вариант 2: приложение локально, БД в Docker

Требуются: JDK 17+, Maven 3.8+, Docker.

```bash
docker compose up -d db
mvn -s settings.xml clean package
java -jar target/social-network.jar
```

или через Maven:

```bash
mvn -s settings.xml spring-boot:run
```

Приложение стартует на `http://localhost:8080`.

Переменные окружения (необязательно, значения по умолчанию указаны):
- `DB_URL` = `jdbc:postgresql://localhost:15432/socialnetwork` (в docker-compose для сервиса `app`: `jdbc:postgresql://db:5432/socialnetwork`)
- `DB_USER` = `sn_user`
- `DB_PASSWORD` = `sn_password`
- `PORT` = `8080`

## Проверка

Импортируйте Postman-коллекцию `postman/SocialNetwork.postman_collection.json`.
Коллекция автоматически сохраняет `user_id` после регистрации и `token` после логина.

Пример вручную:

```bash
# регистрация
curl -s -X POST http://localhost:8080/user/register \
  -H 'Content-Type: application/json' \
  -d '{"first_name":"Иван","second_name":"Иванов","birthdate":"1990-05-15","biography":"Хобби","city":"Москва","gender":"male","password":"secret"}'
# -> {"user_id":"..."}

# логин
curl -s -X POST http://localhost:8080/login \
  -H 'Content-Type: application/json' \
  -d '{"id":"<user_id>","password":"secret"}'
# -> {"token":"..."}

# получение анкеты
curl -s http://localhost:8080/user/get/<user_id> -H 'Authorization: Bearer <token>'
```
