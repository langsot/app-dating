// Формирование подписанной строки initData (как ее передает Telegram) для тестов вне Telegram.
// Запуск из командной строки: node tg_initdata.mjs <telegramId> <имя> [username]
import { createHmac } from "node:crypto";
import { pathToFileURL } from "node:url";

export const DEMO_BOT_TOKEN = "123456789:DEMO-token-for-local-run";

export function makeInitData(botToken, userId, firstName, username = null, authDate = null) {
  const user = { id: userId, first_name: firstName, language_code: "ru" };
  if (username) user.username = username;
  const fields = {
    query_id: `AAH${userId}`,
    user: JSON.stringify(user),
    auth_date: String(authDate ?? Math.floor(Date.now() / 1000)),
  };
  const dcs = Object.keys(fields).sort().map((k) => `${k}=${fields[k]}`).join("\n");
  const secret = createHmac("sha256", "WebAppData").update(botToken).digest();
  fields.hash = createHmac("sha256", secret).update(dcs).digest("hex");
  return new URLSearchParams(fields).toString();
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [id, name, username] = process.argv.slice(2);
  if (!id || !name) {
    console.error("Использование: node tg_initdata.mjs <telegramId> <имя> [username]");
    process.exit(1);
  }
  console.log(makeInitData(process.env.BOT_TOKEN || DEMO_BOT_TOKEN, Number(id), name, username));
}
