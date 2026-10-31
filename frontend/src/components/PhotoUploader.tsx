import { useRef, useState } from "react";
import type { PhotoDto } from "../types";
import { MAX_PHOTOS } from "../logic/profileForm";
import { ApiError, type BackendApiClient } from "../api/BackendApiClient";
import { compressImage } from "./compressImage";

/** Лимит сервера для загружаемого файла. */
const SERVER_LIMIT = 5 * 1024 * 1024;
/** Лимит для исходного снимка: перед отправкой он сжимается. */
const SOURCE_LIMIT = 20 * 1024 * 1024;

const kb = (size: number) => `${Math.round(size / 1024)} КБ`;

export function PhotoUploader({ api, photos, onChange }:
  { api: BackendApiClient; photos: PhotoDto[]; onChange: (p: PhotoDto[]) => void }) {
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  /** Сжатое фото, а если сжать не удалось — исходный файл, когда его примет сервер. */
  async function prepare(file: File): Promise<Blob> {
    try {
      return await compressImage(file);
    } catch (e) {
      console.warn("[photo] сжатие не удалось", e);
      if (["image/jpeg", "image/png"].includes(file.type) && file.size <= SERVER_LIMIT) return file;
      throw new Error(`Не удалось обработать фото (${file.type || "тип неизвестен"}, ${kb(file.size)}): ${e}`);
    }
  }

  async function add(file: File) {
    setError("");
    if (file.type && !file.type.startsWith("image/")) { setError("Выберите изображение"); return; }
    if (file.size > SOURCE_LIMIT) { setError("Размер файла должен быть не более 20 МБ"); return; }
    setBusy(true);
    try {
      const content = await prepare(file);
      console.info(`[photo] ${kb(file.size)} -> ${kb(content.size)}`);
      try {
        const p = await api.uploadPhoto(content);
        onChange([...photos, p]);
      } catch (e) {
        // Причина показывается на экране: на телефоне консоль недоступна
        const reason = e instanceof ApiError ? [e.message, ...e.details].join("; ") : String(e);
        setError(`${reason} (фото ${kb(content.size)}, исходное ${kb(file.size)})`);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
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
          <button type="button" className="photo-add" disabled={busy}
                  onClick={() => input.current?.click()} data-testid="photo-add">{busy ? "…" : "+"}</button>
        )}
      </div>
      {error && <div className="error" data-testid="photo-error">{error}</div>}
      <input ref={input} type="file" accept="image/*" hidden data-testid="photo-input"
             onChange={(e) => { const f = e.target.files?.[0]; if (f) add(f); e.target.value = ""; }} />
    </div>
  );
}
