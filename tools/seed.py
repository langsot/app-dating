"""Наполнение локального экземпляра демонстрационными анкетами через REST API."""
import io, json, os, sys, urllib.request, uuid
from datetime import date
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tg_initdata import make_init_data  # noqa: E402

BASE = "http://localhost:8080"
TOKEN = "123456789:DEMO-token-for-local-run"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
COLORS = [(94, 129, 172), (163, 190, 140), (208, 135, 112), (180, 142, 173), (136, 192, 208), (235, 203, 139)]


def req(method, path, token=None, body=None, raw=None, ctype="application/json"):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    r = urllib.request.Request(BASE + path, data=data, method=method)
    if data is not None:
        r.add_header("Content-Type", ctype)
    if token:
        r.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(r) as resp:
        txt = resp.read().decode()
        return json.loads(txt) if txt else None


def avatar(name, idx, variant=0):
    """Нейтральная заглушка вместо фотографии: цветной фон и инициал."""
    c = COLORS[(idx + variant) % len(COLORS)]
    im = Image.new("RGB", (480, 640), c)
    d = ImageDraw.Draw(im)
    for y in range(640):
        k = y / 640
        d.line([(0, y), (480, y)], fill=tuple(int(v * (1 - 0.35 * k)) for v in c))
    d.ellipse((140, 150, 340, 350), fill=(255, 255, 255, 60), outline=(255, 255, 255), width=6)
    f = ImageFont.truetype(FONT, 120)
    d.text((240, 250), name[0], font=f, fill="white", anchor="mm")
    buf = io.BytesIO()
    im.save(buf, "PNG")
    return buf.getvalue()


def upload(token, png):
    b = "----x" + uuid.uuid4().hex
    body = (f"--{b}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"p.png\"\r\n"
            f"Content-Type: image/png\r\n\r\n").encode() + png + f"\r\n--{b}--\r\n".encode()
    return req("POST", "/api/profile/me/photos", token, raw=body, ctype="multipart/form-data; boundary=" + b)


def login(tg_id, name, username):
    return req("POST", "/api/auth/telegram", body={"initData": make_init_data(TOKEN, tg_id, name, username)})


def person(tg_id, name, born, gender, pref, bio, city, interests, photos=2, min_age=18, max_age=45):
    s = login(tg_id, name, f"user{tg_id}")
    req("PUT", "/api/profile/me", s["token"], {
        "displayName": name, "birthDate": born, "gender": gender, "bio": bio, "city": city,
        "preferredGender": pref, "minAge": min_age, "maxAge": max_age, "interests": interests})
    for v in range(photos):
        upload(s["token"], avatar(name, tg_id, v))
    return s


if __name__ == "__main__":
    people = [
        (2001, "Анна", "2002-03-14", "FEMALE", "MALE", "Люблю походы и настольные игры", "Томск", ["походы", "настольные игры", "кофе"]),
        (2002, "Мария", "1999-07-02", "FEMALE", "MALE", "Фотограф, ищу компанию для прогулок", "Томск", ["фотография", "путешествия"]),
        (2003, "Ольга", "2001-11-20", "FEMALE", "ANY", "Йога по утрам, книги по вечерам", "Северск", ["йога", "книги"]),
        (2004, "Дарья", "2000-01-09", "FEMALE", "MALE", "Танцую сальсу", "Томск", ["танцы", "музыка", "кино"]),
        (2005, "Екатерина", "1998-05-30", "FEMALE", "MALE", "Кулинария и путешествия", "Томск", ["кулинария", "рестораны"]),
        (2006, "Полина", "2003-09-12", "FEMALE", "MALE", "Студентка, люблю театр", "Томск", ["театр", "искусство"]),
        (2007, "Сергей", "1997-02-17", "MALE", "FEMALE", "Инженер, горы и велосипед", "Томск", ["горы", "спорт"]),
    ]
    sessions = {p[0]: person(*p) for p in people}
    me = person(1001, "Евгений", "2000-04-12", "MALE", "FEMALE", "Программист, люблю горы и хороший кофе",
                "Томск", ["горы", "кофе", "IT"], photos=3, min_age=20, max_age=30)
    # Анна и Мария уже поставили лайк Евгению — при его лайке образуется мэтч; Дарья поставила суперлайк
    req("POST", f"/api/feed/{me['userId']}/rate", sessions[2001]["token"], {"type": "LIKE"})
    req("POST", f"/api/feed/{me['userId']}/rate", sessions[2002]["token"], {"type": "LIKE"})
    req("POST", f"/api/feed/{me['userId']}/rate", sessions[2004]["token"], {"type": "SUPER_LIKE"})
    # мэтч с Марией образуется заранее, чтобы в списке мэтчей было несколько записей
    req("POST", f"/api/feed/{sessions[2002]['userId']}/rate", me["token"], {"type": "LIKE"})
    print(json.dumps({"me": me, "users": {k: v["userId"] for k, v in sessions.items()}}, ensure_ascii=False))
