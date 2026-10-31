import { useEffect, useState } from "react";
import type { ScreenProps } from "./Screen";
import type { MatchDto } from "../types";
import { ApiError } from "../api/BackendApiClient";

export function chatLink(m: MatchDto): string | null {
  return m.partnerUsername ? `https://t.me/${m.partnerUsername}` : null;
}

export function relativeDate(iso: string, now: Date = new Date()): string {
  const d = new Date(iso);
  const days = Math.floor((new Date(now.toDateString()).getTime() - new Date(d.toDateString()).getTime()) / 86400000);
  if (days <= 0) return "мэтч сегодня";
  if (days === 1) return "мэтч вчера";
  if (days < 7) return `${days} дн. назад`;
  return d.toLocaleDateString("ru-RU");
}

/** Список мэтчей с переходом в личный чат Telegram. */
export function MatchListScreen({ api, tg }: ScreenProps) {
  const [items, setItems] = useState<MatchDto[] | null>(null);
  const [error, setError] = useState("");

  async function refresh() {
    try { setItems(await api.getMatches()); setError(""); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Не удалось загрузить мэтчи"); }
  }
  useEffect(() => { refresh(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  function openChat(m: MatchDto) {
    const link = chatLink(m);
    if (link) tg.openTelegramLink(link);
    else setError(`У ${m.partnerName} не задано имя пользователя в Telegram — дождитесь сообщения от собеседника`);
  }

  async function unmatch(m: MatchDto) {
    try {
      await api.unmatch(m.id);
      await refresh();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Не удалось разорвать мэтч");
    }
  }

  return (
    <div className="screen matches">
      <h2 className="title">Мэтчи</h2>
      {items === null && !error && <div className="empty">Загрузка…</div>}
      {items?.length === 0 && <div className="empty" data-testid="matches-empty">Пока нет мэтчей. Ставьте лайки в ленте!</div>}
      {items?.map((m) => (
        <div className="match" key={m.id} data-testid="match-item">
          <div className="avatar" style={{ backgroundImage: m.partnerPhotoUrl ? `url(${m.partnerPhotoUrl})` : undefined }} />
          <div className="match-info">
            <h4>{m.partnerName}{m.partnerAge ? `, ${m.partnerAge}` : ""}</h4>
            <p>{relativeDate(m.matchedAt)}</p>
          </div>
          <button className="go" onClick={() => openChat(m)}>Написать</button>
          <button className="link small" onClick={() => unmatch(m)} aria-label="Разорвать мэтч">✕</button>
        </div>
      ))}
      {error && <div className="error">{error}</div>}
    </div>
  );
}
