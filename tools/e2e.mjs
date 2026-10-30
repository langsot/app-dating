// Системная проверка интерфейса в браузере с эмуляцией окружения Telegram Mini App + скриншоты.
import { chromium } from "playwright";
import pg from "pg";
import { mkdirSync, writeFileSync } from "node:fs";
import { DEMO_BOT_TOKEN, makeInitData } from "./tg_initdata.mjs";
import { telegramStub } from "./tg_stub.mjs";

const BASE = process.env.BASE || "http://localhost:8080";
const DATABASE_URL = process.env.DATABASE_URL || "postgres://dating:dating@localhost:5432/dating";
const OUT = process.env.OUT || "build/shots";
mkdirSync(OUT, { recursive: true });
const initData = (id, name) => makeInitData(DEMO_BOT_TOKEN, id, name, `u${id}`);

const results = [];
const check = (id, name, ok, note = "") => { results.push({ id, name, ok, note }); console.log((ok ? "OK  " : "FAIL") + " " + id + " " + name + " " + note); };

async function open(browser, id, name) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 760 }, deviceScaleFactor: 2, locale: "ru-RU" });
  const page = await ctx.newPage();
  page.on("pageerror", (e) => check("JS", "ошибка JavaScript", false, e.message));
  await page.route("https://telegram.org/js/telegram-web-app.js", (r) =>
    r.fulfill({ contentType: "application/javascript", body: telegramStub(initData(id, name), name) }));
  await page.goto(BASE + "/");
  return { ctx, page };
}

const browser = await chromium.launch();

// --- новый пользователь: первый вход открывает редактор анкеты
{
  const { ctx, page } = await open(browser, 3001, "Алексей");
  await page.waitForSelector("text=Моя анкета", { timeout: 10000 }).catch(() => {});
  const editor = await page.locator("input").count();
  check("С-01", "первый вход нового пользователя открывает редактор анкеты", editor > 0);
  await page.screenshot({ path: OUT + "/01_editor_new.png" });
  await page.click("#tg-main-button");
  await page.waitForTimeout(500);
  const errs = await page.locator(".error, .field-error").count();
  check("С-02", "сохранение пустой анкеты: ошибки проверки показаны у полей", errs > 0, `ошибок: ${errs}`);
  await page.screenshot({ path: OUT + "/02_editor_errors.png" });
  await ctx.close();
}

// --- основной пользователь Евгений: лента, суперлайк, мэтч, список мэтчей, анкета
{
  const { ctx, page } = await open(browser, 1001, "Евгений");
  await page.waitForSelector(".card.top", { timeout: 10000 });
  const mb = await page.evaluate(() => { const b = document.getElementById("tg-main-button"); return b ? b.style.display + ":" + b.textContent : "нет"; });
  check("С-08", "на экране ленты системная кнопка MainButton скрыта", !mb.startsWith("flex"), mb);
  if (process.env.ONLY_DEBUG) { await page.screenshot({ path: OUT + "/dbg.png" }); process.exit(0); }
  const firstName = await page.locator(".card.top h2").innerText();
  const badge = await page.locator(".card.top .badge").count();
  check("С-03", "лента: первой показана анкета, поставившая суперлайк", firstName.startsWith("Дарья") && badge === 1, firstName);
  await page.screenshot({ path: OUT + "/03_feed.png" });

  // пролистываем до Анны (она уже поставила лайк) дизлайками остальных
  for (let i = 0; i < 6; i++) {
    const n = await page.locator(".card.top h2").innerText().catch(() => "");
    if (n.startsWith("Анна")) break;
    await page.click("[data-testid=btn-dislike]");
    await page.waitForTimeout(400);
  }
  // свайп мышью вправо
  const box = await page.locator(".card.top").boundingBox();
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  for (let k = 1; k <= 10; k++) { await page.mouse.move(box.x + box.width / 2 + k * 22, box.y + box.height / 2 + k * 2); await page.waitForTimeout(16); }
  await page.screenshot({ path: OUT + "/04_swipe.png" });
  await page.mouse.up();
  const popup = await page.waitForSelector("[data-testid=match-popup]", { timeout: 5000 }).then(() => true).catch(() => false);
  check("С-04", "свайп вправо при встречном лайке: окно «Это мэтч!»", popup);
  await page.screenshot({ path: OUT + "/05_match_popup.png" });
  await page.click("text=Написать в Telegram");
  const log = await page.evaluate(() => window.__tgLog);
  check("С-05", "кнопка «Написать» открывает личный чат Telegram", log.some((l) => l.startsWith("open:https://t.me/")), log.filter((l) => l.startsWith("open")).join(","));

  await page.click("text=Мэтчи");
  await page.waitForTimeout(800);
  const items = await page.locator("[data-testid=match-item], .match").count();
  check("С-06", "список мэтчей содержит два мэтча", items === 2, `элементов: ${items}`);
  await page.screenshot({ path: OUT + "/06_matches.png" });

  await page.click("text=Анкета");
  await page.waitForTimeout(800);
  await page.screenshot({ path: OUT + "/07_editor_filled.png", fullPage: true });
  const nameVal = await page.locator("[data-testid=name]").inputValue();
  check("С-07", "редактор показывает сохраненную анкету", nameVal === "Евгений", nameVal);

  await page.click("text=Лента");
  await page.waitForTimeout(600);
  await page.click("[data-testid=filter]");
  await page.waitForTimeout(300);
  await page.screenshot({ path: OUT + "/08_filter.png" });
  await ctx.close();
}

// --- удаление (деактивация) анкеты и восстановление при повторном входе
{
  const db = new pg.Client({ connectionString: DATABASE_URL });
  await db.connect();
  const userStatus = async (tgId) => (await db.query("select status from users where telegram_id = $1", [tgId])).rows[0]?.status;
  let { ctx, page } = await open(browser, 2007, "Сергей");
  await page.waitForSelector(".tabbar", { timeout: 10000 });
  await page.click("text=Анкета");
  await page.click("[data-testid=delete]");
  const label = await page.locator("[data-testid=delete]").innerText();
  await page.click("[data-testid=delete]");
  const gone = await page.waitForSelector("[data-testid=deleted]", { timeout: 5000 }).then(() => true).catch(() => false);
  const st1 = await userStatus(2007);
  check("С-09", "удаление анкеты: подтверждение вторым нажатием, учетная запись деактивирована", gone && label.includes("еще раз") && st1 === "DEACTIVATED", st1);
  await page.screenshot({ path: OUT + "/09_deleted.png" });
  await ctx.close();
  ({ ctx, page } = await open(browser, 2007, "Сергей"));
  await page.waitForSelector(".tabbar", { timeout: 10000 });
  const st2 = await userStatus(2007);
  check("С-10", "повторный запуск восстанавливает учетную запись", st2 === "ACTIVE", st2);
  await ctx.close();
  await db.end();
}

await browser.close();
writeFileSync(OUT + "/e2e-results.json", JSON.stringify(results, null, 1));
