"""Нагрузочная проверка: 2000 анкет в БД, параллельные клиенты запрашивают ленту и ставят оценки."""
import json, os, random, statistics, subprocess, sys, threading, time, urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tg_initdata import make_init_data  # noqa: E402

BASE = "http://localhost:8080"
TOKEN = "123456789:DEMO-token-for-local-run"
N_PROFILES = int(os.environ.get("N", 2000))
CLIENTS = int(os.environ.get("C", 20))
ROUNDS = int(os.environ.get("R", 30))
PSQL = ["psql", "-q", "-h", "localhost", "-U", "dating", "-d", "dating", "-v", "ON_ERROR_STOP=1"]
env = dict(os.environ, PGPASSWORD="dating")

SEED_SQL = f"""
TRUNCATE likes, matches, photos, profile_interests, profiles, users RESTART IDENTITY CASCADE;
INSERT INTO users (telegram_id, username, first_name, status)
  SELECT 500000 + g, 'load' || g, 'Load' || g, 'ACTIVE' FROM generate_series(1, {N_PROFILES}) g;
INSERT INTO profiles (user_id, display_name, birth_date, gender, bio, city, preferred_gender, min_age, max_age)
  SELECT id, 'Анкета ' || id,
         (current_date - ((18 + (id * 7) % 27) * 365 + (id % 300)) * interval '1 day')::date,
         CASE WHEN id % 2 = 0 THEN 'FEMALE' ELSE 'MALE' END, 'Нагрузочный тест', 'Томск',
         CASE WHEN id % 2 = 0 THEN 'MALE' ELSE 'FEMALE' END, 18, 45
    FROM users;
INSERT INTO photos (profile_id, storage_key, position)
  SELECT id, md5(id::text) || '.png', 0 FROM profiles;
ANALYZE;
"""


def call(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method)
    if data:
        r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    t0 = time.perf_counter()
    with urllib.request.urlopen(r, timeout=30) as resp:
        payload = json.loads(resp.read() or b"null")
        status = resp.status
    return (time.perf_counter() - t0) * 1000, status, payload


def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(round(p / 100 * (len(xs) - 1))))]


def main():
    subprocess.run(PSQL + ["-c", SEED_SQL], check=True, env=env)
    # клиенты — мужские анкеты (нечетные id), ищущие женщин 18–45
    tokens = []
    for i in range(CLIENTS):
        uid = 2 * i + 1
        _, _, a = call("POST", "/api/auth/telegram", body={"initData": make_init_data(TOKEN, 500000 + uid, "Load")})
        tokens.append(a["token"])
    feed_ms, rate_ms, errors, matches = [], [], [], [0]
    lock = threading.Lock()

    def worker(tok):
        rnd = random.Random(tok)
        for _ in range(ROUNDS):
            try:
                ms, st, cards = call("GET", "/api/feed?limit=10", tok)
                with lock:
                    feed_ms.append(ms)
                for c in cards[:3]:
                    t = rnd.choice(["LIKE", "DISLIKE", "SUPER_LIKE"])
                    ms2, st2, res = call("POST", f"/api/feed/{c['userId']}/rate", tok, {"type": t})
                    with lock:
                        rate_ms.append(ms2)
                        matches[0] += 1 if res["matched"] else 0
            except Exception as e:  # noqa: BLE001
                with lock:
                    errors.append(repr(e))

    t0 = time.perf_counter()
    ths = [threading.Thread(target=worker, args=(t,)) for t in tokens]
    [t.start() for t in ths]
    [t.join() for t in ths]
    total = time.perf_counter() - t0
    n = len(feed_ms) + len(rate_ms)
    res = {
        "profiles": N_PROFILES, "clients": CLIENTS, "rounds": ROUNDS, "requests": n, "errors": len(errors),
        "duration_s": round(total, 2), "rps": round(n / total, 1),
        "feed": {"n": len(feed_ms), "avg": round(statistics.mean(feed_ms), 1), "p50": round(pct(feed_ms, 50), 1),
                 "p95": round(pct(feed_ms, 95), 1), "max": round(max(feed_ms), 1)},
        "rate": {"n": len(rate_ms), "avg": round(statistics.mean(rate_ms), 1), "p50": round(pct(rate_ms, 50), 1),
                 "p95": round(pct(rate_ms, 95), 1), "max": round(max(rate_ms), 1)},
        "sample_errors": errors[:3],
    }
    print(json.dumps(res, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
