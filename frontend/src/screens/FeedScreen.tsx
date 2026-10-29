import { useEffect, useRef, useState } from "react";
import type { ScreenProps } from "./Screen";
import type { Direction, FeedCardDto, MatchDto, ProfileDto, SearchFilter } from "../types";
import { FeedQueue } from "../logic/feedQueue";
import { directionToLike } from "../logic/swipe";
import { formFromProfile } from "../logic/profileForm";
import { ApiError } from "../api/BackendApiClient";
import { SwipeCard } from "../components/SwipeCard";
import { FilterPanel } from "../components/FilterPanel";

/** Лента анкет: очередь карточек, свайп, кнопки оценки, фильтр. */
export function FeedScreen({ api, tg, profile, onProfileChanged, onMatch }:
  ScreenProps & { profile: ProfileDto; onProfileChanged: (p: ProfileDto) => void; onMatch: (m: MatchDto) => void }) {
  const queue = useRef(new FeedQueue());
  const [cards, setCards] = useState<FeedCardDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [filterOpen, setFilterOpen] = useState(false);
  const loadingRef = useRef(false);

  async function loadNext() {
    if (loadingRef.current) return;
    loadingRef.current = true;
    try {
      queue.current.append(await api.getFeed(10));
      setError("");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Не удалось загрузить анкеты");
    } finally {
      loadingRef.current = false;
      setLoading(false);
      setCards(queue.current.peek(3));
    }
  }

  useEffect(() => { queue.current.reset(); setLoading(true); loadNext(); }, [profile.preferredGender, profile.minAge, profile.maxAge]); // eslint-disable-line react-hooks/exhaustive-deps

  async function onSwipe(dir: Direction) {
    const type = directionToLike(dir);
    const card = queue.current.current;
    if (!type || !card) return;
    queue.current.pop();
    setCards(queue.current.peek(3));
    tg.haptic(type === "DISLIKE" ? "light" : "medium");
    try {
      const r = await api.rate(card.userId, type);
      if (r.matched && r.match) { tg.haptic("success"); onMatch(r.match); }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Оценка не сохранена");
    }
    if (queue.current.needsRefill()) loadNext();
  }

  async function applyFilter(f: SearchFilter) {
    setFilterOpen(false);
    try {
      const p = await api.saveProfile({ ...formFromProfile(profile), ...f });
      onProfileChanged({ ...p, photos: profile.photos });
    } catch (e) { setError(e instanceof ApiError ? e.message : "Фильтр не сохранен"); }
  }

  const filterText = `${{ ANY: "Все", FEMALE: "Ж", MALE: "М" }[profile.preferredGender]}, ${profile.minAge}–${profile.maxAge}`;
  return (
    <div className="screen feed">
      <div className="feed-head">
        <span>Лента</span>
        <button className="link" onClick={() => setFilterOpen(true)} data-testid="filter">⚙ Фильтр: {filterText}</button>
      </div>
      <div className="stack">
        {loading && <div className="empty">Загрузка…</div>}
        {!loading && cards.length === 0 && (
          <div className="empty" data-testid="feed-empty">
            <p>Анкеты закончились</p>
            <p className="hint">Измените фильтр или загляните позже</p>
          </div>
        )}
        {[...cards].reverse().map((c, i, arr) => (
          <SwipeCard key={c.userId} card={c} top={i === arr.length - 1} onSwipe={onSwipe} />
        ))}
      </div>
      {error && <div className="error center">{error}</div>}
      <div className="rate-buttons">
        <button className="rb nope" disabled={!cards.length} onClick={() => onSwipe("left")} data-testid="btn-dislike">✕</button>
        <button className="rb super" disabled={!cards.length} onClick={() => onSwipe("up")} data-testid="btn-super">★</button>
        <button className="rb like" disabled={!cards.length} onClick={() => onSwipe("right")} data-testid="btn-like">♥</button>
      </div>
      {filterOpen && (
        <FilterPanel value={{ preferredGender: profile.preferredGender, minAge: profile.minAge, maxAge: profile.maxAge }}
                     onApply={applyFilter} onClose={() => setFilterOpen(false)} />
      )}
    </div>
  );
}
