# Веб-приложение для знакомств с интеграцией Telegram Mini Apps

Учебный проект (УПД-4). Серверная часть — Java 25 + Spring Boot 3 + PostgreSQL.

## Структура
- `backend/` — серверная часть (Maven).

## Сборка
```bash
sudo -u postgres psql -c "CREATE ROLE dating LOGIN PASSWORD 'dating'"
sudo -u postgres psql -c "CREATE DATABASE dating OWNER dating"

cd backend && mvn package
```

## Запуск
```bash
export DB_URL=jdbc:postgresql://localhost:5432/dating DB_USER=dating DB_PASSWORD=dating
java -jar backend/target/dating-app-1.0.0.jar
```
