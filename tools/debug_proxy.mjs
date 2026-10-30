// Отладка вне Telegram: прокси к локальному приложению, заменяющий telegram-web-app.js заглушкой
// с подписанными демо-токеном initData. С настоящим BOT_TOKEN такая подпись не пройдет проверку.
// Пользователь выбирается в адресе: http://localhost:8080/?user=1001&name=Евгений
import { createServer, request } from "node:http";
import { DEMO_BOT_TOKEN, makeInitData } from "./tg_initdata.mjs";
import { telegramStub } from "./tg_stub.mjs";

const APP = new URL(process.env.APP_URL || "http://app:8080");
const PORT = Number(process.env.PROXY_PORT || 8080);
const TELEGRAM_SCRIPT = "https://telegram.org/js/telegram-web-app.js";

function identity(url) {
  const id = Number(url.searchParams.get("user")) || 1001;
  const name = url.searchParams.get("name") || "Евгений";
  return { id, name, initData: makeInitData(DEMO_BOT_TOKEN, id, name, `user${id}`) };
}

function proxy(req, res, url) {
  const isPage = req.method === "GET" && url.pathname === "/";
  const headers = { ...req.headers, host: APP.host };
  if (isPage) {
    // Страница переписывается, поэтому нужен полный несжатый ответ
    for (const h of ["accept-encoding", "if-modified-since", "if-none-match"]) delete headers[h];
  }
  const upstream = request({ host: APP.hostname, port: APP.port, method: req.method, path: req.url, headers }, (r) => {
    if (!isPage) {
      res.writeHead(r.statusCode, r.headers);
      r.pipe(res);
      return;
    }
    const chunks = [];
    r.on("data", (c) => chunks.push(c));
    r.on("end", () => {
      const html = Buffer.concat(chunks).toString("utf8")
        .replace(TELEGRAM_SCRIPT, "/__debug/telegram.js" + url.search);
      const { "content-length": _len, "last-modified": _mod, etag: _etag, ...rest } = r.headers;
      res.writeHead(r.statusCode, rest);
      res.end(html);
    });
  });
  upstream.on("error", (e) => {
    res.writeHead(502, { "content-type": "text/plain; charset=utf-8" });
    res.end(`Приложение недоступно: ${e.message}\n`);
  });
  req.pipe(upstream);
}

createServer((req, res) => {
  const url = new URL(req.url, "http://localhost");
  if (url.pathname === "/__debug/telegram.js") {
    const { name, initData } = identity(url);
    res.writeHead(200, { "content-type": "application/javascript; charset=utf-8", "cache-control": "no-store" });
    res.end(telegramStub(initData, name));
  } else if (url.pathname === "/__debug/initdata") {
    res.writeHead(200, { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" });
    res.end(identity(url).initData);
  } else {
    proxy(req, res, url);
  }
}).listen(PORT, () => console.log(`Отладочный вход: http://localhost:${PORT}/?user=1001&name=Евгений`));
