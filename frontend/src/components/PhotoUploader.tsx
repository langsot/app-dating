import { useEffect, useRef, useState } from "react";
import type { PendingPhoto, PhotoDto } from "../types";
import { MAX_PHOTOS } from "../logic/profileForm";
import { ApiError, type BackendApiClient } from "../api/BackendApiClient";

/**
 * Фотографии анкеты. Пока анкета не создана (deferred), выбранные файлы только показываются
 * и ждут первого сохранения: сервер принимает фото лишь для существующей анкеты.
 */
export function PhotoUploader({ api, photos, onChange, pending, onPendingChange, deferred }:
  { api: BackendApiClient; photos: PhotoDto[]; onChange: (p: PhotoDto[]) => void;
    pending: PendingPhoto[]; onPendingChange: (p: PendingPhoto[]) => void; deferred: boolean }) {
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  // После создания анкеты ошибка выбора файла уже неактуальна
  useEffect(() => { setError(""); }, [deferred]);

  async function add(file: File) {
    setError("");
    if (!["image/jpeg", "image/png"].includes(file.type)) { setError("Допускаются только JPEG и PNG"); return; }
    if (file.size > 5 * 1024 * 1024) { setError("Размер файла должен быть не более 5 МБ"); return; }
    if (deferred) {
      onPendingChange([...pending, { file, url: URL.createObjectURL(file) }]);
      return;
    }
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

  function removePending(item: PendingPhoto) {
    URL.revokeObjectURL(item.url);
    onPendingChange(pending.filter((p) => p !== item));
  }

  return (
    <div>
      <div className="photo-grid">
        {photos.map((p) => (
          <div key={p.id} className="photo" style={{ backgroundImage: `url(${p.url})` }}>
            <button type="button" className="photo-del" onClick={() => remove(p.id)} aria-label="Удалить">✕</button>
          </div>
        ))}
        {pending.map((p) => (
          <div key={p.url} className="photo" style={{ backgroundImage: `url(${p.url})` }} data-testid="photo-pending">
            <button type="button" className="photo-del" onClick={() => removePending(p)} aria-label="Удалить">✕</button>
          </div>
        ))}
        {photos.length + pending.length < MAX_PHOTOS && (
          <button type="button" className="photo-add" disabled={busy}
                  onClick={() => input.current?.click()} data-testid="photo-add">{busy ? "…" : "+"}</button>
        )}
      </div>
      {pending.length > 0 && <div className="hint">Фотографии загрузятся при сохранении анкеты</div>}
      {error && <div className="error">{error}</div>}
      <input ref={input} type="file" accept="image/jpeg,image/png" hidden data-testid="photo-input"
             onChange={(e) => { const f = e.target.files?.[0]; if (f) add(f); e.target.value = ""; }} />
    </div>
  );
}
