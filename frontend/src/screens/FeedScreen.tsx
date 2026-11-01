import { useEffect, useRef, useState } from "react";
import type { ScreenProps } from "./Screen";
import type { Direction, FeedCardDto, MatchDto, ProfileDto, SearchFilter } from "../types";
import { FeedQueue } from "../logic/feedQueue";
import { directionToLike } from "../logic/swipe";
import { formFromProfile } from "../logic/profileForm";
import { ApiError } from "../api/BackendApiClient";
import { SwipeCard } from "../components/SwipeCard";
import { FilterPanel } from "../components/FilterPanel";

/** Очередь ленты живет дольше экрана, чтобы переключение вкладок не сбрасывало ее. */
export interface FeedState { queue: FeedQueue; filter: string }

/** Лента анкет: очередь карточек, свайп, кнопки оценки, фильтр. */
export function FeedScreen({ api, tg, profile, feed, onProfileChanged, onMatch }:
  ScreenProps & { profile: ProfileDto; feed: FeedState; onProfileChanged: (p: ProfileDto) => void; onMatch: (m: MatchDto) => void }) {
  const queue = useRef(feed.queue);
  const [cards, setCards] = useState<FeedCardDto[]>(feed.queue.peek(3));
  const [loading, setLoading] = useState(feed.queue.size === 0);
  const [error, setError] = useState("");
  const [filterOpen, setFilterOpen] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const loadingRef = useRef(false);
  /** Номер запроса: ответ, пришедший после смены фильтра, отбрасывается. */
  const generation = useRef(0);

  async function loadNext() {
    if (loadingRef.current) return;
    loadingRef.current = true;
    const current = generation.current;
    let batch: FeedCardDto[] | null = null;
    let failure = "";
    try {
      batch = await api.getFeed(10);
    } catch (e) {
      failure = e instanceof ApiError ? e.message : "Не удалось загрузить анкеты";
    }
    loadingRef.current = false;
    if (current !== generation.current) { loadNext(); return; }
    if (batch) queue.current.append(batch);
    setError(failure);
    setLoadFailed(!!failure);
    setLoading(false);
    setCards(queue.current.peek(3));
  }

  function reload() {
    generation.current++;
    queue.current.reset();
    setCards([]);
    setLoading(true);
    setLoadFailed(false);
    loadNext();
  }

  useEffect(() => {
    // При возврате на вкладку очередь остается прежней, если фильтр не менялся
    const filter = `${profile.preferredGender}|${profile.minAge}|${profile.maxAge}`;
    if (feed.filter === filter && feed.queue.size > 0) return;
    feed.filter = filter;
    reload();
  }, [profile.preferredGender, profile.minAge, profile.maxAge]); // eslint-disable-line react-hooks/exhaustive-deps

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
      queue.current.restore(card);
      setCards(queue.current.peek(3));
      setError(e instanceof ApiError ? e.message : "Оценка не сохранена");
      return;
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
        {!loading && cards.length === 0 && loadFailed && (
          <div className="empty" data-testid="feed-error">
            <p>Не удалось загрузить анкеты</p>
            <button className="btn" onClick={reload}>Повторить</button>
          </div>
        )}
        {!loading && cards.length === 0 && !loadFailed && (
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
