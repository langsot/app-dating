import { useState } from "react";
import type { GenderPreference, SearchFilter } from "../types";

export function FilterPanel({ value, onApply, onClose }:
  { value: SearchFilter; onApply: (f: SearchFilter) => void; onClose: () => void }) {
  const [f, setF] = useState<SearchFilter>(value);
  const invalid = f.minAge < 18 || f.maxAge > 99 || f.minAge > f.maxAge;
  return (
    <div className="sheet">
      <h3>Фильтр</h3>
      <label>Показывать</label>
      <select value={f.preferredGender} onChange={(e) => setF({ ...f, preferredGender: e.target.value as GenderPreference })}>
        <option value="ANY">Всех</option><option value="FEMALE">Женщин</option><option value="MALE">Мужчин</option>
      </select>
      <label>Возраст: от {f.minAge} до {f.maxAge}</label>
      <div className="row">
        <input type="number" min={18} max={99} value={f.minAge} onChange={(e) => setF({ ...f, minAge: Number(e.target.value) })} />
        <input type="number" min={18} max={99} value={f.maxAge} onChange={(e) => setF({ ...f, maxAge: Number(e.target.value) })} />
      </div>
      {invalid && <div className="error">Некорректный диапазон возраста</div>}
      <div className="row">
        <button className="btn secondary" onClick={onClose}>Отмена</button>
        <button className="btn" disabled={invalid} onClick={() => onApply(f)}>Применить</button>
      </div>
    </div>
  );
}
