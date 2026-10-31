import { useRef, useState } from "react";
import type { Direction, FeedCardDto } from "../types";
import { rotationFor, swipeDirection } from "../logic/swipe";

/** Карточка анкеты с жестом свайпа. */
export function SwipeCard({ card, onSwipe, top }:
  { card: FeedCardDto; onSwipe: (d: Direction) => void; top: boolean }) {
  const ref = useRef<HTMLDivElement>(null);
  const start = useRef<{ x: number; y: number } | null>(null);
  const [d, setD] = useState({ x: 0, y: 0 });
  const last = useRef({ x: 0, y: 0 });
  const [photo, setPhoto] = useState(0);

  function onDown(e: React.PointerEvent) {
    if (!top) return;
    start.current = { x: e.clientX, y: e.clientY };
    last.current = { x: 0, y: 0 };
    (e.target as Element).setPointerCapture?.(e.pointerId);
  }
  function onMove(e: React.PointerEvent) {
    if (!start.current) return;
    last.current = { x: e.clientX - start.current.x, y: e.clientY - start.current.y };
    setD(last.current);
  }
  function onUp() {
    if (!start.current) return;
    start.current = null;
    const width = ref.current?.offsetWidth ?? 300;
    const m = last.current;
    const dir = swipeDirection(m.x, m.y, width);
    if (dir === "none") {
      // Короткое касание листает фотографии
      if (Math.abs(m.x) < 5 && Math.abs(m.y) < 5 && card.photoUrls.length > 1) setPhoto((photo + 1) % card.photoUrls.length);
      setD({ x: 0, y: 0 });
    } else onSwipe(dir);
  }

  /** Система перехватила жест (например, Telegram): карточка возвращается на место. */
  function onCancel() {
    start.current = null;
    setD({ x: 0, y: 0 });
  }

  const width = ref.current?.offsetWidth ?? 300;
  const style = { transform: `translate(${d.x}px, ${d.y}px) rotate(${rotationFor(d.x, width)}deg)` };
  const label = swipeDirection(d.x, d.y, width);
  return (
    <div ref={ref} className={top ? "card top" : "card"} style={top ? style : undefined}
         onPointerDown={onDown} onPointerMove={onMove} onPointerUp={onUp} onPointerCancel={onCancel} data-testid={top ? "card-top" : undefined}>
      <div className="card-photo" style={{ backgroundImage: card.photoUrls[photo] ? `url(${card.photoUrls[photo]})` : undefined }} />
      {card.photoUrls.length > 1 && (
        <div className="dots">{card.photoUrls.map((_, i) => <span key={i} className={i === photo ? "on" : ""} />)}</div>
      )}
      {card.superLikedYou && <div className="badge">★ Вы понравились</div>}
      {label === "right" && <div className="stamp like">LIKE</div>}
      {label === "left" && <div className="stamp nope">NOPE</div>}
      {label === "up" && <div className="stamp super">SUPER</div>}
      <div className="card-info">
        <h2>{card.displayName}, {card.age}</h2>
        {card.city && <p>{card.city}</p>}
        {card.bio && <p className="bio">{card.bio}</p>}
        <div className="chips">{card.interests.map((i) => <span key={i} className="chip light">{i}</span>)}</div>
      </div>
    </div>
  );
}
