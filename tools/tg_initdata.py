"""Формирование подписанной строки initData (как ее передает Telegram) для тестов вне Telegram."""
import hashlib, hmac, json, time, urllib.parse


def make_init_data(bot_token: str, user_id: int, first_name: str, username: str | None = None,
                   auth_date: int | None = None) -> str:
    user = {"id": user_id, "first_name": first_name, "language_code": "ru"}
    if username:
        user["username"] = username
    fields = {
        "query_id": f"AAH{user_id}",
        "user": json.dumps(user, ensure_ascii=False, separators=(",", ":")),
        "auth_date": str(auth_date or int(time.time())),
    }
    dcs = "\n".join(f"{k}={fields[k]}" for k in sorted(fields))
    secret = hmac.new(b"WebAppData", bot_token.encode(), hashlib.sha256).digest()
    fields["hash"] = hmac.new(secret, dcs.encode(), hashlib.sha256).hexdigest()
    return urllib.parse.urlencode(fields)
