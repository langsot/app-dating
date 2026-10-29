import { useEffect, useState } from "react";
import type { ScreenProps } from "./Screen";
import type { Gender, GenderPreference, InterestDto, PhotoDto, ProfileDto, ProfileForm } from "../types";
import { emptyForm, formFromProfile, MAX_BIO, validateProfile } from "../logic/profileForm";
import { ApiError } from "../api/BackendApiClient";
import { PhotoUploader } from "../components/PhotoUploader";
import { InterestSelector } from "../components/InterestSelector";

/** Редактор анкеты. Сохранение — системной кнопкой Telegram MainButton (или кнопкой формы вне Telegram). */
export function ProfileEditorScreen({ api, tg, profile, onSaved, onDeleted }:
  ScreenProps & { profile: ProfileDto | null; onSaved: (p: ProfileDto) => void; onDeleted: () => void }) {
  const [form, setForm] = useState<ProfileForm>(profile ? formFromProfile(profile) : emptyForm(tg.firstName));
  const [photos, setPhotos] = useState<PhotoDto[]>(profile?.photos ?? []);
  const [all, setAll] = useState<InterestDto[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState("");
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);
  const [hidden, setHidden] = useState(profile?.hidden ?? false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  useEffect(() => { api.getInterests().then(setAll).catch(() => setAll([])); }, [api]);

  async function save() {
    const e = validateProfile(form);
    setErrors(e); setServerError(""); setSaved(false);
    if (Object.keys(e).length) { tg.haptic("error"); return; }
    setBusy(true); tg.setMainButtonProgress(true);
    try {
      const p = await api.saveProfile(form);
      setSaved(true); tg.haptic("success");
      onSaved({ ...p, photos });
    } catch (err) {
      setServerError(err instanceof ApiError ? [err.message, ...err.details].join("; ") : "Не удалось сохранить");
      tg.haptic("error");
    } finally { setBusy(false); tg.setMainButtonProgress(false); }
  }

  useEffect(() => {
    if (tg.hasMainButton) tg.showMainButton("СОХРАНИТЬ", save);
    return () => tg.hideMainButton();
  });

  /** Удаление анкеты в два нажатия, чтобы исключить случайную деактивацию. */
  async function deleteProfile() {
    if (!confirmDelete) { setConfirmDelete(true); tg.haptic("medium"); return; }
    try {
      await api.deactivate();
      tg.haptic("success");
      onDeleted();
    } catch (err) {
      setServerError(err instanceof ApiError ? err.message : "Не удалось удалить анкету");
      setConfirmDelete(false);
    }
  }

  async function toggleHidden() {
    await api.setHidden(!hidden);
    setHidden(!hidden);
  }

  const set = <K extends keyof ProfileForm>(k: K, v: ProfileForm[K]) => setForm({ ...form, [k]: v });
  const photosChanged = (p: PhotoDto[]) => { setPhotos(p); if (profile) onSaved({ ...profile, photos: p, complete: p.length > 0 }); };

  return (
    <div className="screen editor">
      <h2 className="title">{profile ? "Моя анкета" : "Создание анкеты"}</h2>
      <label>Фотографии (до 6)</label>
      <PhotoUploader api={api} photos={photos} onChange={photosChanged} disabled={!profile} />

      <div className="row">
        <div className="field"><label>Имя</label>
          <input value={form.displayName} maxLength={64} onChange={(e) => set("displayName", e.target.value)} data-testid="name" />
          {errors.displayName && <div className="error">{errors.displayName}</div>}</div>
        <div className="field"><label>Дата рождения</label>
          <input type="date" value={form.birthDate} onChange={(e) => set("birthDate", e.target.value)} data-testid="birth" />
          {errors.birthDate && <div className="error">{errors.birthDate}</div>}</div>
      </div>
      <div className="row">
        <div className="field"><label>Пол</label>
          <select value={form.gender} onChange={(e) => set("gender", e.target.value as Gender)} data-testid="gender">
            <option value="">—</option><option value="MALE">Мужской</option><option value="FEMALE">Женский</option>
          </select>
          {errors.gender && <div className="error">{errors.gender}</div>}</div>
        <div className="field"><label>Ищу</label>
          <select value={form.preferredGender} onChange={(e) => set("preferredGender", e.target.value as GenderPreference)} data-testid="pref">
            <option value="ANY">Всех</option><option value="FEMALE">Женщин</option><option value="MALE">Мужчин</option>
          </select></div>
      </div>
      <div className="row">
        <div className="field"><label>Возраст от</label>
          <input type="number" min={18} max={99} value={form.minAge} onChange={(e) => set("minAge", Number(e.target.value))} /></div>
        <div className="field"><label>до</label>
          <input type="number" min={18} max={99} value={form.maxAge} onChange={(e) => set("maxAge", Number(e.target.value))} /></div>
      </div>
      {errors.ageRange && <div className="error">{errors.ageRange}</div>}
      <label>Город</label>
      <input value={form.city} maxLength={64} onChange={(e) => set("city", e.target.value)} data-testid="city" />
      <label>О себе ({form.bio.length}/{MAX_BIO})</label>
      <textarea value={form.bio} rows={3} onChange={(e) => set("bio", e.target.value)} data-testid="bio" />
      {errors.bio && <div className="error">{errors.bio}</div>}
      <label>Интересы</label>
      <InterestSelector all={all} selected={form.interests} onChange={(s) => set("interests", s)} />

      {serverError && <div className="error">{serverError}</div>}
      {saved && <div className="ok">Анкета сохранена{photos.length === 0 ? ". Добавьте фотографию, чтобы появиться в ленте" : ""}</div>}
      {!tg.hasMainButton && <button className="btn wide" disabled={busy} onClick={save} data-testid="save">Сохранить</button>}
      {profile && (
        <button className="link" onClick={toggleHidden}>{hidden ? "Показать анкету в ленте" : "Скрыть анкету из ленты"}</button>
      )}
      {profile && (
        <button className="link danger" onClick={deleteProfile} data-testid="delete">
          {confirmDelete ? "Нажмите еще раз, чтобы удалить анкету" : "Удалить анкету"}</button>
      )}
    </div>
  );
}
