// Cloudflare Worker: прокси к Telegram Bot API для сервера, с которого api.telegram.org недоступен.
// Пропускает только POST /bot<токен>/sendMessage. Токен не хранится и не пишется в журнал.
const SEND_MESSAGE = /^\/bot\d+:[\w-]+\/sendMessage$/;

export default {
  async fetch(request) {
    const url = new URL(request.url);
    if (request.method !== "POST" || !SEND_MESSAGE.test(url.pathname)) {
      return new Response("Not found", { status: 404 });
    }
    return fetch(`https://api.telegram.org${url.pathname}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: await request.text(),
    });
  },
};
