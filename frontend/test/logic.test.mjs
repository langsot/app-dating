// Модульные тесты логики клиента (node --test). Перед запуском: node build.mjs --test
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  swipeDirection, directionToLike, rotationFor,
  ageOn, validateProfile, emptyForm, toggleInterest, MAX_INTERESTS,
  FeedQueue, stepErrors, fitSize, BackendApiClient,
} from "./build/logic.mjs";

const card = (id) => ({ userId: id, displayName: "U" + id, age: 25, city: null, bio: null,
  photoUrls: [], interests: [], superLikedYou: false });

test("К-01 жест: смещение больше 30 % ширины карточки — лайк/дизлайк, вверх — суперлайк", () => {
  assert.equal(swipeDirection(120, 0, 300), "right");
  assert.equal(swipeDirection(-120, 0, 300), "left");
  assert.equal(swipeDirection(50, 0, 300), "none");
  assert.equal(swipeDirection(10, -150, 300), "up");
  assert.equal(directionToLike("right"), "LIKE");
  assert.equal(directionToLike("left"), "DISLIKE");
  assert.equal(directionToLike("up"), "SUPER_LIKE");
  assert.equal(directionToLike("none"), null);
});

test("К-02 поворот карточки ограничен ±15°", () => {
  assert.equal(rotationFor(1000, 300), 15);
  assert.equal(rotationFor(-1000, 300), -15);
  assert.equal(rotationFor(0, 300), 0);
});

test("К-03 возраст считается по полным годам", () => {
  const today = new Date("2026-10-27T12:00:00");
  assert.equal(ageOn("2008-10-27", today), 18);
  assert.equal(ageOn("2008-10-28", today), 17);
});

test("К-04 проверка формы анкеты на клиенте", () => {
  const today = new Date("2026-10-27T12:00:00");
  const f = { ...emptyForm("Анна"), birthDate: "2001-05-01", gender: "FEMALE" };
  assert.deepEqual(validateProfile(f, today), {});
  assert.ok(validateProfile({ ...f, displayName: "  " }, today).displayName);
  assert.equal(validateProfile({ ...f, birthDate: "2010-01-01" }, today).birthDate, "Сервис доступен с 18 лет");
  assert.ok(validateProfile({ ...f, bio: "x".repeat(501) }, today).bio);
  assert.ok(validateProfile({ ...f, minAge: 30, maxAge: 20 }, today).ageRange);
});

test("К-05 не более 10 интересов; повторный выбор снимает интерес", () => {
  let s = [];
  for (let i = 0; i < 12; i++) s = toggleInterest(s, "i" + i);
  assert.equal(s.length, MAX_INTERESTS);
  assert.deepEqual(toggleInterest(["кино", "IT"], "кино"), ["IT"]);
});

test("К-06 очередь ленты: без дубликатов, догрузка при 2 карточках, пустой пакет — конец ленты", () => {
  const q = new FeedQueue();
  assert.equal(q.append([card(1), card(2), card(3), card(4)]), 4);
  assert.equal(q.append([card(4), card(5)]), 1);
  assert.equal(q.size, 5);
  q.pop(); q.pop();
  assert.equal(q.needsRefill(), false);
  q.pop();
  assert.equal(q.needsRefill(), true);
  q.append([]);
  assert.equal(q.exhausted, true);
  assert.equal(q.needsRefill(), false);
});

test("К-07 шаг регистрации проверяет только свои поля", () => {
  const today = new Date(2026, 0, 1);
  const f = emptyForm("");
  assert.deepEqual(Object.keys(stepErrors(f, 0, today)).sort(), ["birthDate", "displayName"]);
  assert.deepEqual(Object.keys(stepErrors(f, 1, today)), ["gender"]);
  assert.deepEqual(stepErrors(f, 2, today), {});
  assert.deepEqual(stepErrors(f, 5, today), {});
  assert.deepEqual(Object.keys(stepErrors({ ...f, minAge: 40, maxAge: 20 }, 2, today)), ["ageRange"]);
});

test("К-08 сжатие: большая сторона до 1280 px, пропорции сохраняются, мелкие снимки не растут", () => {
  assert.deepEqual(fitSize(4000, 3000), { width: 1280, height: 960 });
  assert.deepEqual(fitSize(3000, 4000), { width: 960, height: 1280 });
  assert.deepEqual(fitSize(800, 600), { width: 800, height: 600 });
});

test("К-09 при обрыве соединения анкета отправляется повторно с теми же данными, фото — нет", async () => {
  const bodies = [];
  const api = new BackendApiClient("", async (url, init) => {
    bodies.push(init.body);
    if (bodies.length === 1) throw new TypeError("Load failed");
    return new Response(JSON.stringify({ displayName: "Аня" }), { status: 200 });
  });
  const saved = await api.saveProfile(emptyForm("Аня"));
  assert.equal(saved.displayName, "Аня");
  assert.equal(bodies.length, 2);
  assert.equal(bodies[1], bodies[0]);

  let uploads = 0;
  const photoApi = new BackendApiClient("", async () => { uploads++; throw new TypeError("Load failed"); });
  await assert.rejects(photoApi.uploadPhoto(new Blob(["x"])), { status: 0 });
  assert.equal(uploads, 1);
});
