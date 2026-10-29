# Сборка в три этапа: клиент (Node) -> сервер (Maven) -> легкий образ запуска (JRE)

# ---------- 1. Клиент Telegram Mini App
FROM node:22-alpine AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
# build.mjs кладет результат в ../backend/src/main/resources/static
RUN mkdir -p /src/backend/src/main/resources && npm test && npm run build

# ---------- 2. Серверная часть
FROM maven:3.9-eclipse-temurin-25 AS backend
WORKDIR /src/backend
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /src/backend/src/main/resources/static ./src/main/resources/static
# Интеграционным тестам нужна PostgreSQL, поэтому при сборке образа они пропускаются
# (запуск тестов — см. DOCKER.md, раздел «Тесты»)
RUN mvn -q -B -DskipTests package

# ---------- 3. Образ запуска
FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 app && mkdir -p /data/photos && chown -R app /data
WORKDIR /app
COPY --from=backend /src/backend/target/dating-app-1.0.0.jar app.jar
USER app
ENV STORAGE_DIR=/data/photos PORT=8080
VOLUME /data/photos
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
