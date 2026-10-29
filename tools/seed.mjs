// Наполнение локального экземпляра демонстрационными анкетами через REST API.
import { avatar } from "./avatar.mjs";
import { DEMO_BOT_TOKEN, makeInitData } from "./tg_initdata.mjs";

const BASE = process.env.BASE || "http://localhost:8080";

async function req(method, path, token = null, body = undefined) {
  const headers = {};
  if (token) headers.Authorization = "Bearer " + token;
  if (body !== undefined && !(body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(body);
  }
  const res = await fetch(BASE + path, { method, headers, body });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path}: HTTP ${res.status} ${text}`);
  return text ? JSON.parse(text) : null;
}

function upload(token, png) {
  const form = new FormData();
  form.append("file", new Blob([png], { type: "image/png" }), "p.png");
  return req("POST", "/api/profile/me/photos", token, form);
}

function login(tgId, name, username) {
  return req("POST", "/api/auth/telegram", null, { initData: makeInitData(DEMO_BOT_TOKEN, tgId, name, username) });
}

async function person(tgId, name, born, gender, pref, bio, city, interests,
                      { photos = 2, minAge = 18, maxAge = 45 } = {}) {
  const s = await login(tgId, name, `user${tgId}`);
  await req("PUT", "/api/profile/me", s.token, {
    displayName: name, birthDate: born, gender, bio, city,
    preferredGender: pref, minAge, maxAge, interests,
  });
  for (let v = 0; v < photos; v++) await upload(s.token, avatar(tgId, v));
  return s;
}

const people = [
  [2001, "Анна", "2002-03-14", "FEMALE", "MALE", "Люблю походы и настольные игры", "Томск", ["походы", "настольные игры", "кофе"]],
  [2002, "Мария", "1999-07-02", "FEMALE", "MALE", "Фотограф, ищу компанию для прогулок", "Томск", ["фотография", "путешествия"]],
  [2003, "Ольга", "2001-11-20", "FEMALE", "ANY", "Йога по утрам, книги по вечерам", "Северск", ["йога", "книги"]],
  [2004, "Дарья", "2000-01-09", "FEMALE", "MALE", "Танцую сальсу", "Томск", ["танцы", "музыка", "кино"]],
  [2005, "Екатерина", "1998-05-30", "FEMALE", "MALE", "Кулинария и путешествия", "Томск", ["кулинария", "рестораны"]],
  [2006, "Полина", "2003-09-12", "FEMALE", "MALE", "Студентка, люблю театр", "Томск", ["театр", "искусство"]],
  [2007, "Сергей", "1997-02-17", "MALE", "FEMALE", "Инженер, горы и велосипед", "Томск", ["горы", "спорт"]],
];

const sessions = {};
for (const p of people) sessions[p[0]] = await person(...p);
const me = await person(1001, "Евгений", "2000-04-12", "MALE", "FEMALE", "Программист, люблю горы и хороший кофе",
  "Томск", ["горы", "кофе", "IT"], { photos: 3, minAge: 20, maxAge: 30 });

// Анна и Мария уже поставили лайк Евгению — при его лайке образуется мэтч; Дарья поставила суперлайк
await req("POST", `/api/feed/${me.userId}/rate`, sessions[2001].token, { type: "LIKE" });
await req("POST", `/api/feed/${me.userId}/rate`, sessions[2002].token, { type: "LIKE" });
await req("POST", `/api/feed/${me.userId}/rate`, sessions[2004].token, { type: "SUPER_LIKE" });
// мэтч с Марией образуется заранее, чтобы в списке мэтчей было несколько записей
await req("POST", `/api/feed/${sessions[2002].userId}/rate`, me.token, { type: "LIKE" });

const users = Object.fromEntries(Object.entries(sessions).map(([k, v]) => [k, v.userId]));
console.log(JSON.stringify({ me, users }, null, 1));
