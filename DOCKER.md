# Запуск в Docker на своем компьютере со статическим IP

Состав: `db` (PostgreSQL 17), `app` (приложение, собирается из исходников), `caddy`
(HTTPS с автоматическим сертификатом Let's Encrypt). Наружу открыты только порты 80 и 443.

## 1. Что нужно заранее
- Docker и Docker Compose v2 (`docker compose version`). На Windows и macOS — Docker Desktop.
- Белый статический IP, и порты **80/TCP и 443/TCP** (желательно и 443/UDP) проброшены
  на роутере к этому компьютеру и разрешены в брандмауэре. Порт 80 нужен Let's Encrypt
  для выдачи сертификата, 443 — для работы приложения.
- Бот Telegram: в @BotFather команда `/newbot`, сохраните токен.

## 2. Адрес: домен или IP
Telegram открывает мини-приложения только по HTTPS с настоящим сертификатом.
- **Домен (надежнее).** Бесплатный поддомен на duckdns.org, указывающий на ваш IP, —
  `DOMAIN=mydating.duckdns.org`. Без регистрации можно `DOMAIN=203-0-113-10.sslip.io`
  (подставьте свой IP через дефисы).
- **Только IP.** `DOMAIN=203.0.113.10`. Let's Encrypt выдает на IP сертификат сроком
  около 6 суток; Caddy продлевает его сам. Если сертификат не выдается, используйте домен.

## 3. Настройка
```bash
git clone https://github.com/langsot/app-dating.git && cd app-dating
cp .env.example .env
openssl rand -hex 32    # -> JWT_SECRET
openssl rand -hex 16    # -> DB_PASSWORD
nano .env               # DOMAIN, BOT_TOKEN, JWT_SECRET, DB_PASSWORD
```

## 4. Запуск
```bash
docker compose up -d --build      # первая сборка 5–10 минут
docker compose logs -f caddy app  # ждать «certificate obtained successfully» и «Started DatingApplication»
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
| В журнале caddy ошибка получения сертификата | Порт 80 недоступен снаружи: проверьте проброс на роутере и брандмауэр; откройте `http://<IP>` с телефона через мобильный интернет. Некоторые провайдеры блокируют 80 — используйте домен и DNS-проверку или другой провайдер |
| Мини-приложение в Telegram пустое или с ошибкой | Сертификат еще не выдан или DOMAIN не совпадает с адресом в BotFather |
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
gh secret set SSH_HOST --env production                      # IP или домен сервера
gh secret set SSH_USER --env production --body deploy
gh secret set SSH_PRIVATE_KEY --env production < deploy_key
gh secret set SSH_KNOWN_HOSTS --env production < known_hosts
gh secret set BOT_TOKEN --env production                     # токен от @BotFather
gh secret set JWT_SECRET --env production --body "$(openssl rand -hex 32)"
gh secret set DB_PASSWORD --env production --body "$(openssl rand -hex 16)"
gh variable set DOMAIN --env production --body mydating.duckdns.org
rm deploy_key known_hosts                                    # копии больше не нужны
```

| Имя | Тип | Назначение |
|---|---|---|
| `SSH_HOST`, `SSH_USER` | секрет | куда подключаться |
| `SSH_PRIVATE_KEY` | секрет | приватный ключ деплоя |
| `SSH_KNOWN_HOSTS` | секрет | отпечаток сервера: без него подключение отклоняется |
| `BOT_TOKEN`, `JWT_SECRET`, `DB_PASSWORD` | секрет | попадают в `.env` на сервере (права 600) |
| `DOMAIN` | переменная | адрес приложения, публичный |
| `SSH_PORT`, `APP_DIR` | переменная, необязательно | порт SSH (22) и каталог проекта (`app-dating` в домашнем каталоге) |

Важно:
- Используйте значения из `openssl rand -hex`: символ `$` в `.env` Docker Compose воспринимает как подстановку.
- `DB_PASSWORD` применяется только при первом создании тома БД. Чтобы сменить пароль позже:
  `docker compose exec db psql -U dating -c "ALTER USER dating PASSWORD '<новый>'"`, затем обновить секрет.
- Деплой выкладывает на сервер ровно тот коммит, который прошел CI; не правьте файлы в каталоге проекта на сервере.
- При утечке: `/revoke` в @BotFather, новый `JWT_SECRET` (пользователи просто войдут заново),
  удалить строку ключа из `authorized_keys` и выпустить новый ключ.
