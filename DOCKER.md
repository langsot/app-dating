# Запуск в Docker на сервере

Состав: `db` (PostgreSQL 17), `app` (приложение, собирается из исходников), `tunnel`
(Cloudflare Tunnel). HTTPS и сертификат обеспечивает Cloudflare; `tunnel` сам устанавливает
исходящее соединение, поэтому входящие порты для приложения на сервере не открываются.

## 1. Что нужно заранее
- Docker и Docker Compose v2 (`docker compose version`). На Windows и macOS — Docker Desktop.
- Домен, DNS которого обслуживает Cloudflare (бесплатный тариф подходит).
- Бот Telegram: в @BotFather команда `/newbot`, сохраните токен.
- Для автодеплоя (п. 9) — GitHub self-hosted runner на сервере; входящие порты не нужны.

## 2. Туннель Cloudflare
Telegram открывает мини-приложения только по HTTPS с настоящим сертификатом — его выдает Cloudflare.
1. Cloudflare Zero Trust → Networks → Tunnels → **Create a tunnel** → тип Cloudflared, имя, например `dating`.
2. На шаге установки выберите Docker и скопируйте токен из показанной команды (длинная строка после `--token`).
   Саму команду запускать не нужно — туннель запустит `docker compose`.
3. Добавьте публичный адрес (Public hostname): поддомен, например `dating`, ваш домен,
   URL сервиса **`http://app:8080`**. DNS-запись Cloudflare создаст сам; старую A- или AAAA-запись
   с тем же именем сначала удалите.

## 3. Настройка
```bash
git clone https://github.com/langsot/app-dating.git && cd app-dating
cp .env.example .env
openssl rand -hex 32    # -> JWT_SECRET
openssl rand -hex 16    # -> DB_PASSWORD
nano .env               # DOMAIN, CLOUDFLARE_TUNNEL_TOKEN, BOT_TOKEN, JWT_SECRET, DB_PASSWORD
chmod 600 .env
```

## 4. Запуск
```bash
docker compose up -d --build       # первая сборка 5–10 минут
docker compose logs -f tunnel app  # ждать «Registered tunnel connection» и «Started DatingApplication»
```
Проверка в браузере: `https://<DOMAIN>/api/interests` — список интересов в JSON;
`https://<DOMAIN>/` — надпись «Откройте приложение из бота…» (вне Telegram так и должно быть).

## 5. Подключение бота
```bash
source .env
curl "https://api.telegram.org/bot$BOT_TOKEN/setWebhook?url=https://$DOMAIN/api/telegram/webhook"
```
В @BotFather: `/mybots` → ваш бот → Bot Settings → Menu Button → URL `https://<DOMAIN>/`.
Теперь в чате с ботом `/start` → кнопка «Открыть знакомства».

## 6. Тесты в Docker
```bash
docker compose --profile tests run --rm tests     # 45 тестов на PostgreSQL
```
База `dating_test` создается при первом создании тома БД. Если том был создан раньше:
`docker compose exec db psql -U dating -d dating -c "CREATE DATABASE dating_test"`.

## Локальная отладка без Telegram
Отдельная копия приложения со своей базой и демо-токеном бота; рабочие контейнеры и данные не затрагиваются.
```bash
docker compose -f docker-compose.debug.yml up -d --build
docker compose -f docker-compose.debug.yml run --rm seed     # демо-анкеты, один раз
```
Откройте в браузере `http://localhost:8080/?user=1001&name=Евгений`. Другой `user` — другой пользователь.
Подписанные данные для запросов к API: `http://localhost:8080/__debug/initdata?user=1001`.
Проверить системную кнопку и открытие чатов можно только в настоящем Telegram, а уведомления бота здесь не отправляются.
Остановить: `docker compose -f docker-compose.debug.yml down` (с `-v` — удалить и данные отладки).

## 7. Обслуживание
```bash
docker compose ps                      # состояние
docker compose logs -f app             # журнал приложения
docker compose down                    # остановить (данные сохраняются в томах)
docker compose up -d --build           # обновить после изменения кода
docker compose exec db pg_dump -U dating dating > backup.sql     # резервная копия БД
```
Фотографии хранятся в томе `photos`, база — в `db-data`. `docker compose down -v` удалит все данные.

## Логи в Grafana Cloud
Логи контейнеров отправляет Alloy, ошибки и логи клиента — Grafana Faro. Без настроек ниже приложение работает как обычно, просто без отправки.

1. Grafana Cloud → **Loki** → Details: скопируйте URL (`.../loki/api/v1/push`) и User. Создайте токен с правом записи логов (Access Policies → `logs:write`).
2. Grafana Cloud → **Frontend** → создайте приложение и скопируйте адрес сбора (Faro URL). В разрешенные адреса (CORS) добавьте домен приложения.
3. Задайте значения в окружении `production` и запустите Deploy:
```bash
gh variable set FARO_URL --env production --body "<адрес сбора>"
gh variable set GRAFANA_LOKI_URL --env production --body "<URL Loki>"
gh variable set GRAFANA_LOKI_USER --env production --body "<User>"
gh secret set GRAFANA_LOKI_TOKEN --env production
```
Логи сервера: Explore → Loki, запрос `{app="app-dating", service="app"}`. Ошибки клиента — в разделе Frontend.
Данные входа Telegram из адреса страницы вырезаются перед отправкой. Адрес сбора виден в коде клиента: так и задумано, он только принимает данные.

## 8. Если что-то не работает
| Признак | Причина и решение |
|---|---|
| В журнале tunnel «Provided Tunnel token is not valid» | Неверный `CLOUDFLARE_TUNNEL_TOKEN`: скопируйте токен заново из панели туннеля |
| Ошибка Cloudflare 1033 | Туннель не запущен: `docker compose ps`, `docker compose logs tunnel` |
| Ошибка 502 Bad Gateway | В Public hostname указан не тот сервис: должен быть `http://app:8080` (не `localhost`) |
| Ошибка 522 | Домен ведет на IP, а не в туннель: удалите старую A-запись и добавьте Public hostname (п. 2) |
| Приложение не запускается: «Секрет JWT должен содержать не менее 32 символов» | `JWT_SECRET` не задан или слишком короткий |
| Мини-приложение в Telegram пустое или с ошибкой | `DOMAIN` не совпадает с адресом туннеля или с URL в BotFather |
| «Подпись initData неверна» | BOT_TOKEN в .env не от того бота, который открывает приложение |
| Ошибки CORS / 403 на запросах | Приложение открыто по адресу, отличному от DOMAIN |
| Бот не отвечает на /start | Не выполнен setWebhook (п. 5): `curl https://api.telegram.org/bot$BOT_TOKEN/getWebhookInfo` |

## 9. Автодеплой из GitHub Actions
Workflow `CI` проверяет каждый PR и пуш в `main`. После успешного CI на `main` workflow `Deploy`
запускается на сервере через self-hosted runner и перезапускает контейнеры (ручной запуск:
Actions → Deploy → Run workflow). Секреты хранятся только в GitHub: файл `.env` не создается,
пункты 3–4 вручную делать не нужно. Workflow рассчитан на сервер с Windows и Docker Desktop.

**В GitHub** — Settings → Environments → New environment `production`. Рекомендуется
Deployment branches: только `main`. Затем секреты и переменные окружения:
```bash
gh secret set CLOUDFLARE_TUNNEL_TOKEN --env production       # токен туннеля (п. 2)
gh secret set BOT_TOKEN --env production                     # токен от @BotFather
gh secret set JWT_SECRET --env production --body "$(openssl rand -hex 32)"
gh secret set DB_PASSWORD --env production --body "$(openssl rand -hex 16)"
gh variable set DOMAIN --env production --body dating.example.com
```

**Защита раннера.** Репозиторий публичный, а раннер выполняет код на вашем компьютере. Settings →
Actions → General → Fork pull request workflows: **Require approval for all external contributors**.
Не подтверждайте запуск workflow в чужих PR: такой PR может изменить workflow и выполнить код на раннере.

**На сервере** — Settings → Actions → Runners → New self-hosted runner → Windows x64. Выполните
показанные команды в PowerShell, раннер разместите, например, в `C:\actions-runner` (этот же путь
укажите в команде автозапуска ниже). При настройке (`config.cmd`)
добавьте метку `app-dating`, на вопрос о запуске как службы ответьте `N`: Docker Desktop работает
в сеансе пользователя. Автозапуск при входе в Windows (PowerShell от администратора):
```powershell
schtasks /Create /TN github-runner /SC ONLOGON /TR "C:\actions-runner\run.cmd"
```
В Docker Desktop включите Settings → General → Start Docker Desktop when you sign in.
Отключите сон при работе от сети: без него раннер и приложение недоступны.

| Имя | Тип | Назначение |
|---|---|---|
| `CLOUDFLARE_TUNNEL_TOKEN`, `BOT_TOKEN`, `JWT_SECRET`, `DB_PASSWORD` | секрет | передаются контейнерам при деплое |
| `DOMAIN` | переменная | адрес приложения, публичный |

Важно:
- `DB_PASSWORD` применяется только при первом создании тома БД. Чтобы сменить пароль позже:
  `docker exec app-dating-db-1 psql -U dating -c "ALTER USER dating PASSWORD '<новый>'"`, затем обновить секрет.
- Деплой выкладывает ровно тот коммит, который прошел CI. Журнал приложения: `docker logs -f app-dating-app-1`.
- При утечке: `/revoke` в @BotFather, новый `JWT_SECRET` (пользователи просто войдут заново),
  токен туннеля — Refresh token в панели туннеля; раннер — удалить в Settings → Actions → Runners.
