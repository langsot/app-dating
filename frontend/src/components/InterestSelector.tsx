import type { InterestDto } from "../types";
import { MAX_INTERESTS, toggleInterest } from "../logic/profileForm";

export function InterestSelector({ all, selected, onChange }:
  { all: InterestDto[]; selected: string[]; onChange: (s: string[]) => void }) {
  return (
    <div>
      <div className="chips">
        {all.map((i) => (
          <button type="button" key={i.name} className={selected.includes(i.name) ? "chip on" : "chip"}
                  onClick={() => onChange(toggleInterest(selected, i.name))}>{i.name}</button>
        ))}
      </div>
      <div className="hint">Выбрано {selected.length} из {MAX_INTERESTS}</div>
    </div>
  );
}
