# Веб-приложение для знакомств с интеграцией Telegram Mini Apps

Учебный проект (УПД-4). Серверная часть — Java 25 + Spring Boot 3 + PostgreSQL,
клиент — TypeScript + React, работает как Telegram Mini App.

## Структура
- `backend/` — серверная часть (Maven): `web` (REST-контроллеры), `service` (бизнес-логика),
  `repository` (Spring Data JPA), `domain` (сущности), миграции Flyway в `src/main/resources/db/migration`.
- `frontend/` — клиент мини-приложения; сборка копируется в `backend/src/main/resources/static`.
- `tools/` — вспомогательные сценарии: `tg_initdata.py` (подпись initData как в Telegram),
  `seed.py` (демо-данные), `load.py` (нагрузочный тест), `e2e.mjs` (системный тест в браузере, Playwright).

## Сборка и тесты
```bash
# база для интеграционных тестов
sudo -u postgres psql -c "CREATE ROLE dating LOGIN PASSWORD 'dating'"
sudo -u postgres psql -c "CREATE DATABASE dating OWNER dating"
sudo -u postgres psql -c "CREATE DATABASE dating_test OWNER dating"

cd frontend && npm install && npm test && npm run build
cd ../backend && mvn test          # 45 тестов JUnit (нужен PostgreSQL с базой dating_test)
mvn package -DskipTests            # target/dating-app-1.0.0.jar
```
Адрес тестовой базы можно переопределить переменной `TEST_DB_URL`.

## Запуск
```bash
export BOT_TOKEN=<токен от @BotFather> JWT_SECRET=<не менее 32 символов> \
       DB_URL=jdbc:postgresql://localhost:5432/dating DB_USER=dating DB_PASSWORD=dating \
       WEB_APP_URL=https://<ваш-домен> STORAGE_DIR=./data/photos
java -jar backend/target/dating-app-1.0.0.jar
curl "https://api.telegram.org/bot$BOT_TOKEN/setWebhook?url=$WEB_APP_URL/api/telegram/webhook"
```
Telegram открывает мини-приложения только по HTTPS — используйте Nginx с сертификатом.
