import type { ScreenId } from "../types";

const TABS: { id: ScreenId; label: string; icon: string }[] = [
  { id: "feed", label: "Лента", icon: "●" },
  { id: "matches", label: "Мэтчи", icon: "♥" },
  { id: "editor", label: "Анкета", icon: "☺" },
];

export function TabBar({ current, onSelect }: { current: ScreenId; onSelect: (s: ScreenId) => void }) {
  return (
    <nav className="tabbar">
      {TABS.map((t) => (
        <button key={t.id} className={t.id === current ? "tab on" : "tab"} onClick={() => onSelect(t.id)}
                data-testid={`tab-${t.id}`}>
          <span className="tab-icon">{t.icon}</span>{t.label}
        </button>
      ))}
    </nav>
  );
}
