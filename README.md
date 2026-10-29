# Веб-приложение для знакомств с интеграцией Telegram Mini Apps

Учебный проект (УПД-4). Серверная часть — Java 25 + Spring Boot 4 + PostgreSQL,
клиент — TypeScript + React, работает как Telegram Mini App.

## Структура
- `backend/` — серверная часть (Maven): `web` (REST-контроллеры), `service` (бизнес-логика),
  `repository` (Spring Data JPA), `domain` (сущности), миграции Flyway в `src/main/resources/db/migration`.
- `frontend/` — клиент мини-приложения; сборка копируется в `backend/src/main/resources/static`.
- `tools/` — вспомогательные сценарии на Node.js: `tg_initdata.mjs` (подпись initData как в Telegram),
  `seed.mjs` (демо-данные), `load.mjs` (нагрузочный тест), `e2e.mjs` (системный тест в браузере, Playwright).

## Запуск в Docker (рекомендуется)
См. [DOCKER.md](DOCKER.md): `cp .env.example .env`, заполнить, `docker compose up -d --build`.
Автодеплой из GitHub Actions после успешного CI — раздел 9 там же.

## Сборка и тесты без Docker
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

## Вспомогательные сценарии
Сценарии рассчитаны на бэкенд, запущенный с демо-токеном `BOT_TOKEN=123456789:DEMO-token-for-local-run`.
```bash
cd tools && npm install
npx playwright install chromium    # браузер для e2e
npm run initdata -- 42 Иван        # подписанная строка initData для ручных запросов
npm run seed                       # демо-анкеты, лайки и мэтч
npm run e2e                        # системный тест, скриншоты в tools/build/shots (после seed)
N=2000 C=20 R=30 npm run load      # нагрузочный тест (очищает БД!)
```
Адреса переопределяются переменными `BASE` (по умолчанию `http://localhost:8080`)
и `DATABASE_URL` (по умолчанию `postgres://dating:dating@localhost:5432/dating`).
