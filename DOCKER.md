# Запуск в Docker на сервере

Состав: `db` (PostgreSQL 17), `app` (приложение, собирается из исходников), `tunnel`
(Cloudflare Tunnel). HTTPS и сертификат обеспечивает Cloudflare; `tunnel` сам устанавливает
исходящее соединение, поэтому входящие порты для приложения на сервере не открываются.

## 1. Что нужно заранее
- Docker и Docker Compose v2 (`docker compose version`). На Windows и macOS — Docker Desktop.
- Домен, DNS которого обслуживает Cloudflare (бесплатный тариф подходит).
- Бот Telegram: в @BotFather команда `/newbot`, сохраните токен.
- Для автодеплоя (п. 9) — доступ к серверу по SSH (порт 22).

## 2. Туннель Cloudflare
Telegram открывает мини-приложения только по HTTPS с настоящим сертификатом — его выдает Cloudflare.
1. Cloudflare Zero Trust → Networks → Tunnels → **Create a tunnel** → тип Cloudflared, имя, например `dating`.
2. На шаге установки выберите Docker и скопируйте токен из показанной команды (длинная строка после `--token`).
   Саму команду запускать не нужно — туннель запустит `docker compose`.
3. Добавьте публичный адрес (Public hostname): поддомен, например `dating`, ваш домен,
   сервис **HTTP**, URL **`app:8080`**. DNS-запись Cloudflare создаст сам.

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

## 7. Обслуживание
```bash
docker compose ps                      # состояние
docker compose logs -f app             # журнал приложения
docker compose down                    # остановить (данные сохраняются в томах)
docker compose up -d --build           # обновить после изменения кода
docker compose exec db pg_dump -U dating dating > backup.sql     # резервная копия БД
```
Фотографии хранятся в томе `photos`, база — в `db-data`. `docker compose down -v` удалит все данные.

## 8. Если что-то не работает
| Признак | Причина и решение |
|---|---|
| В журнале tunnel «Provided Tunnel token is not valid» | Неверный `CLOUDFLARE_TUNNEL_TOKEN`: скопируйте токен заново из панели туннеля |
| Ошибка Cloudflare 1033 | Туннель не запущен: `docker compose ps`, `docker compose logs tunnel` |
| Ошибка 502 Bad Gateway | В Public hostname указан не тот сервис: должен быть HTTP и `app:8080` (не `localhost`) |
| Мини-приложение в Telegram пустое или с ошибкой | `DOMAIN` не совпадает с адресом туннеля или с URL в BotFather |
| «Подпись initData неверна» | BOT_TOKEN в .env не от того бота, который открывает приложение |
| Ошибки CORS / 403 на запросах | Приложение открыто по адресу, отличному от DOMAIN |
| Бот не отвечает на /start | Не выполнен setWebhook (п. 5): `curl https://api.telegram.org/bot$BOT_TOKEN/getWebhookInfo` |

## 9. Автодеплой из GitHub Actions
Workflow `CI` проверяет каждый PR и пуш в `main`. После успешного CI на `main` workflow `Deploy`
заходит на сервер по SSH, записывает `.env` из секретов GitHub и перезапускает контейнеры
(ручной запуск: Actions → Deploy → Run workflow). Пункты 3–4 тогда вручную делать не нужно.

**На сервере** — отдельный пользователь для деплоя (группа `docker` дает права root, поэтому
ключ этого пользователя храните только в секретах GitHub):
```bash
sudo adduser --disabled-password --gecos "" deploy
sudo usermod -aG docker deploy
sudo -iu deploy git clone https://github.com/langsot/app-dating.git
```

SSH — единственный открытый порт, закройте вход по паролю и все остальные порты:
```bash
# в /etc/ssh/sshd_config: PasswordAuthentication no и PermitRootLogin no
sudo systemctl restart ssh
sudo ufw default deny incoming && sudo ufw allow 22/tcp && sudo ufw enable
```
Не добавляйте `ports:` сервисам в `docker-compose.yml`: опубликованные Docker-порты обходят ufw.

**На своем компьютере** — ключ только для деплоя и отпечаток сервера:
```bash
ssh-keygen -t ed25519 -f deploy_key -N "" -C github-actions-deploy
# содержимое deploy_key.pub добавить в /home/deploy/.ssh/authorized_keys на сервере
ssh-keyscan -H <IP сервера> > known_hosts
# сверить отпечаток с выводом на сервере: ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
```

**В GitHub** — Settings → Environments → New environment `production`. Рекомендуется
Deployment branches: только `main`; по желанию Required reviewers — деплой после подтверждения.
Затем секреты и переменные окружения (значения вводятся скрыто или берутся из файлов):
```bash
gh secret set SSH_HOST --env production                      # IP сервера
gh secret set SSH_USER --env production --body deploy
gh secret set SSH_PRIVATE_KEY --env production < deploy_key
gh secret set SSH_KNOWN_HOSTS --env production < known_hosts
gh secret set CLOUDFLARE_TUNNEL_TOKEN --env production       # токен туннеля (п. 2)
gh secret set BOT_TOKEN --env production                     # токен от @BotFather
gh secret set JWT_SECRET --env production --body "$(openssl rand -hex 32)"
gh secret set DB_PASSWORD --env production --body "$(openssl rand -hex 16)"
gh variable set DOMAIN --env production --body dating.example.com
rm deploy_key known_hosts                                    # копии больше не нужны
```

| Имя | Тип | Назначение |
|---|---|---|
| `SSH_HOST`, `SSH_USER` | секрет | куда подключаться |
| `SSH_PRIVATE_KEY` | секрет | приватный ключ деплоя |
| `SSH_KNOWN_HOSTS` | секрет | отпечаток сервера: без него подключение отклоняется |
| `CLOUDFLARE_TUNNEL_TOKEN`, `BOT_TOKEN`, `JWT_SECRET`, `DB_PASSWORD` | секрет | попадают в `.env` на сервере (права 600) |
| `DOMAIN` | переменная | адрес приложения, публичный |
| `SSH_PORT`, `APP_DIR` | переменная, необязательно | порт SSH (22) и каталог проекта (`app-dating` в домашнем каталоге) |

Важно:
- Используйте значения из `openssl rand -hex`: символ `$` в `.env` Docker Compose воспринимает как подстановку.
- `DB_PASSWORD` применяется только при первом создании тома БД. Чтобы сменить пароль позже:
  `docker compose exec db psql -U dating -c "ALTER USER dating PASSWORD '<новый>'"`, затем обновить секрет.
- Деплой выкладывает на сервер ровно тот коммит, который прошел CI; не правьте файлы в каталоге проекта на сервере.
- При утечке: `/revoke` в @BotFather, новый `JWT_SECRET` (пользователи просто войдут заново),
  удалить строку ключа из `authorized_keys` и выпустить новый ключ; токен туннеля — Refresh token в панели туннеля.
