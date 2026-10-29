import type { Direction, LikeType } from "../types";

/** Порог смещения карточки (в долях ширины), после которого жест считается свайпом. */
export const SWIPE_THRESHOLD = 0.3;
/** Порог вертикального смещения для суперлайка, пикселей. */
export const SUPER_THRESHOLD_PX = 120;

/** Определяет направление свайпа по смещению карточки. */
export function swipeDirection(dx: number, dy: number, cardWidth: number): Direction {
  if (cardWidth <= 0) return "none";
  if (-dy > SUPER_THRESHOLD_PX && Math.abs(dx) < cardWidth * SWIPE_THRESHOLD) return "up";
  if (dx > cardWidth * SWIPE_THRESHOLD) return "right";
  if (dx < -cardWidth * SWIPE_THRESHOLD) return "left";
  return "none";
}

/** Соответствие направления свайпа и типа оценки. */
export function directionToLike(dir: Direction): LikeType | null {
  switch (dir) {
    case "right": return "LIKE";
    case "left": return "DISLIKE";
    case "up": return "SUPER_LIKE";
    default: return null;
  }
}

/** Угол поворота карточки при перетаскивании, градусов (не более 15). */
export function rotationFor(dx: number, cardWidth: number): number {
  if (cardWidth <= 0) return 0;
  const r = (dx / cardWidth) * 20;
  return Math.max(-15, Math.min(15, r));
}
