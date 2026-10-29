import type { FeedCardDto } from "../types";

/** Когда в очереди остается столько карточек, запрашивается следующий пакет. */
export const REFILL_AT = 2;

/** Очередь карточек ленты: убирает дубликаты и подсказывает, когда догружать. */
export class FeedQueue {
  private items: FeedCardDto[] = [];
  private seen = new Set<number>();
  exhausted = false;

  get size(): number { return this.items.length; }
  get current(): FeedCardDto | undefined { return this.items[0]; }
  peek(n: number): FeedCardDto[] { return this.items.slice(0, n); }

  /** Добавляет пакет с сервера; пустой пакет означает, что анкеты закончились. */
  append(batch: FeedCardDto[]): number {
    if (batch.length === 0) { this.exhausted = true; return 0; }
    let added = 0;
    for (const c of batch) {
      if (!this.seen.has(c.userId)) { this.seen.add(c.userId); this.items.push(c); added++; }
    }
    if (added === 0) this.exhausted = true;
    return added;
  }

  /** Убирает текущую карточку после оценки. */
  pop(): FeedCardDto | undefined { return this.items.shift(); }

  needsRefill(): boolean { return !this.exhausted && this.items.length <= REFILL_AT; }

  reset(): void { this.items = []; this.seen.clear(); this.exhausted = false; }
}
