import { useRef, useState } from "react";
import type { PhotoDto } from "../types";
import { MAX_PHOTOS } from "../logic/profileForm";
import { ApiError, type BackendApiClient } from "../api/BackendApiClient";

export function PhotoUploader({ api, photos, onChange, disabled }:
  { api: BackendApiClient; photos: PhotoDto[]; onChange: (p: PhotoDto[]) => void; disabled?: boolean }) {
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function add(file: File) {
    setError("");
    if (!["image/jpeg", "image/png"].includes(file.type)) { setError("Допускаются только JPEG и PNG"); return; }
    if (file.size > 5 * 1024 * 1024) { setError("Размер файла должен быть не более 5 МБ"); return; }
    setBusy(true);
    try {
      const p = await api.uploadPhoto(file);
      onChange([...photos, p]);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Не удалось загрузить фото");
    } finally { setBusy(false); }
  }

  async function remove(id: number) {
    try {
      await api.deletePhoto(id);
      onChange(photos.filter((p) => p.id !== id));
    } catch (e) { setError(e instanceof ApiError ? e.message : "Не удалось удалить фото"); }
  }

  return (
    <div>
      <div className="photo-grid">
        {photos.map((p) => (
          <div key={p.id} className="photo" style={{ backgroundImage: `url(${p.url})` }}>
            <button type="button" className="photo-del" onClick={() => remove(p.id)} aria-label="Удалить">✕</button>
          </div>
        ))}
        {photos.length < MAX_PHOTOS && (
          <button type="button" className="photo-add" disabled={busy || disabled}
                  onClick={() => input.current?.click()} data-testid="photo-add">{busy ? "…" : "+"}</button>
        )}
      </div>
      {disabled && <div className="hint">Фотографии можно добавить после первого сохранения анкеты</div>}
      {error && <div className="error">{error}</div>}
      <input ref={input} type="file" accept="image/jpeg,image/png" hidden data-testid="photo-input"
             onChange={(e) => { const f = e.target.files?.[0]; if (f) add(f); e.target.value = ""; }} />
    </div>
  );
}
