// Нагрузочная проверка: 2000 анкет в БД, параллельные клиенты запрашивают ленту и ставят оценки.
import pg from "pg";
import { DEMO_BOT_TOKEN, makeInitData } from "./tg_initdata.mjs";

const BASE = process.env.BASE || "http://localhost:8080";
const DATABASE_URL = process.env.DATABASE_URL || "postgres://dating:dating@localhost:5432/dating";
const N_PROFILES = Number(process.env.N || 2000);
const CLIENTS = Number(process.env.C || 20);
const ROUNDS = Number(process.env.R || 30);

const SEED_SQL = `
TRUNCATE likes, matches, photos, profile_interests, profiles, users RESTART IDENTITY CASCADE;
INSERT INTO users (telegram_id, username, first_name, status)
  SELECT 500000 + g, 'load' || g, 'Load' || g, 'ACTIVE' FROM generate_series(1, ${N_PROFILES}) g;
INSERT INTO profiles (user_id, display_name, birth_date, gender, bio, city, preferred_gender, min_age, max_age)
  SELECT id, 'Анкета ' || id,
         (current_date - ((18 + (id * 7) % 27) * 365 + (id % 300)) * interval '1 day')::date,
         CASE WHEN id % 2 = 0 THEN 'FEMALE' ELSE 'MALE' END, 'Нагрузочный тест', 'Томск',
         CASE WHEN id % 2 = 0 THEN 'MALE' ELSE 'FEMALE' END, 18, 45
    FROM users;
INSERT INTO photos (profile_id, storage_key, position)
  SELECT id, md5(id::text) || '.png', 0 FROM profiles;
ANALYZE;
`;

async function call(method, path, token = null, body = undefined) {
  const headers = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers.Authorization = "Bearer " + token;
  const t0 = performance.now();
  const res = await fetch(BASE + path, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(30_000),
  });
  const text = await res.text();
  const ms = performance.now() - t0;
  if (!res.ok) throw new Error(`${method} ${path}: HTTP ${res.status} ${text}`);
  return { ms, payload: text ? JSON.parse(text) : null };
}

const round1 = (x) => Math.round(x * 10) / 10;

function stats(xs) {
  if (xs.length === 0) return { n: 0 };
  const s = [...xs].sort((a, b) => a - b);
  const pct = (p) => s[Math.min(s.length - 1, Math.round((p / 100) * (s.length - 1)))];
  return {
    n: s.length, avg: round1(s.reduce((a, b) => a + b, 0) / s.length),
    p50: round1(pct(50)), p95: round1(pct(95)), max: round1(s[s.length - 1]),
  };
}

const db = new pg.Client({ connectionString: DATABASE_URL });
await db.connect();
await db.query(SEED_SQL);
await db.end();

// клиенты — мужские анкеты (нечетные id), ищущие женщин 18–45
const tokens = [];
for (let i = 0; i < CLIENTS; i++) {
  const uid = 2 * i + 1;
  const { payload } = await call("POST", "/api/auth/telegram", null,
    { initData: makeInitData(DEMO_BOT_TOKEN, 500000 + uid, "Load") });
  tokens.push(payload.token);
}

const feedMs = [];
const rateMs = [];
const errors = [];
let matches = 0;

async function worker(token) {
  for (let r = 0; r < ROUNDS; r++) {
    try {
      const feed = await call("GET", "/api/feed?limit=10", token);
      feedMs.push(feed.ms);
      for (const card of feed.payload.slice(0, 3)) {
        const type = ["LIKE", "DISLIKE", "SUPER_LIKE"][Math.floor(Math.random() * 3)];
        const rate = await call("POST", `/api/feed/${card.userId}/rate`, token, { type });
        rateMs.push(rate.ms);
        if (rate.payload.matched) matches++;
      }
    } catch (e) {
      errors.push(String(e));
    }
  }
}

const t0 = performance.now();
await Promise.all(tokens.map(worker));
const total = (performance.now() - t0) / 1000;
const n = feedMs.length + rateMs.length;

console.log(JSON.stringify({
  profiles: N_PROFILES, clients: CLIENTS, rounds: ROUNDS, requests: n, errors: errors.length, matches,
  duration_s: Math.round(total * 100) / 100, rps: round1(n / total),
  feed: stats(feedMs), rate: stats(rateMs), sample_errors: errors.slice(0, 3),
}, null, 1));
