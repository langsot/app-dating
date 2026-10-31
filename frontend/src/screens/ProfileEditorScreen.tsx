import { useEffect, useState } from "react";
import type { ScreenProps } from "./Screen";
import type { Gender, GenderPreference, InterestDto, PhotoDto, ProfileDto, ProfileForm } from "../types";
import { formFromProfile, MAX_BIO, validateProfile } from "../logic/profileForm";
import { ApiError } from "../api/BackendApiClient";
import { PhotoUploader } from "../components/PhotoUploader";
import { InterestSelector } from "../components/InterestSelector";

/** Редактор анкеты. Сохранение — системной кнопкой Telegram MainButton (или кнопкой формы вне Telegram). */
export function ProfileEditorScreen({ api, tg, profile, onSaved, onDeleted }:
  ScreenProps & { profile: ProfileDto; onSaved: (p: ProfileDto) => void; onDeleted: () => void }) {
  const [form, setForm] = useState<ProfileForm>(formFromProfile(profile));
  const [photos, setPhotos] = useState<PhotoDto[]>(profile.photos);
  // Анкета открывается для просмотра; поля меняются только в режиме редактирования
  const [editing, setEditing] = useState(false);
  const [all, setAll] = useState<InterestDto[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState("");
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);
  const [hidden, setHidden] = useState(profile.hidden);
  const [confirmDelete, setConfirmDelete] = useState(false);

  useEffect(() => { api.getInterests().then(setAll).catch(() => setAll([])); }, [api]);

  async function save() {
    if (busy) return;
    const e = validateProfile(form);
    setErrors(e); setServerError(""); setSaved(false);
    if (Object.keys(e).length) { tg.haptic("error"); return; }
    setBusy(true); tg.setMainButtonProgress(true);
    try {
      const p = await api.saveProfile(form);
      setSaved(true); tg.haptic("success");
      onSaved({ ...p, photos, complete: photos.length > 0, hidden });
      setEditing(false);
    } catch (err) {
      setServerError(err instanceof ApiError ? [err.message, ...err.details].join("; ") : "Не удалось сохранить");
      tg.haptic("error");
    } finally { setBusy(false); tg.setMainButtonProgress(false); }
  }

  useEffect(() => {
    if (editing && tg.hasMainButton) tg.showMainButton("СОХРАНИТЬ", save);
    return () => tg.hideMainButton();
  });

  function startEditing() {
    setForm(formFromProfile(profile));
    setErrors({}); setServerError(""); setSaved(false); setConfirmDelete(false);
    setEditing(true);
  }

  function cancelEditing() {
    setErrors({}); setServerError("");
    setEditing(false);
  }

  /** Удаление учетной записи в два нажатия, чтобы исключить случайное удаление. */
  async function deleteProfile() {
    if (!confirmDelete) { setConfirmDelete(true); tg.haptic("medium"); return; }
    try {
      await api.deleteAccount();
      tg.haptic("success");
      onDeleted();
    } catch (err) {
      setServerError(err instanceof ApiError ? err.message : "Не удалось удалить учетную запись");
      setConfirmDelete(false);
    }
  }

  async function toggleHidden() {
    setServerError("");
    try {
      await api.setHidden(!hidden);
      setHidden(!hidden);
      onSaved({ ...profile, hidden: !hidden });
    } catch (err) {
      setServerError(err instanceof ApiError ? err.message : "Не удалось изменить видимость анкеты");
    }
  }

  const set = <K extends keyof ProfileForm>(k: K, v: ProfileForm[K]) => { setSaved(false); setForm({ ...form, [k]: v }); };
  const photosChanged = (p: PhotoDto[]) => { setPhotos(p); onSaved({ ...profile, photos: p, complete: p.length > 0 }); };

  if (!editing) {
    return (
      <div className="screen profile-view" data-testid="profile-view">
        <h2 className="title">Моя анкета</h2>
        {photos.length > 0
          ? <div className="photo-grid">{photos.map((p) => <div key={p.id} className="photo" style={{ backgroundImage: `url(${p.url})` }} />)}</div>
          : <div className="hint">Добавьте фотографию, чтобы появиться в ленте</div>}
        <ViewRow label="Имя" value={profile.displayName} testId="view-name" />
        <ViewRow label="Дата рождения" value={`${new Date(profile.birthDate + "T00:00:00").toLocaleDateString("ru-RU")} (${profile.age})`} />
        <ViewRow label="Пол" value={profile.gender === "MALE" ? "Мужской" : "Женский"} />
        <ViewRow label="Ищу" value={{ ANY: "Всех", FEMALE: "Женщин", MALE: "Мужчин" }[profile.preferredGender]} />
        <ViewRow label="Возраст" value={`от ${profile.minAge} до ${profile.maxAge}`} />
        {profile.city && <ViewRow label="Город" value={profile.city} />}
        {profile.bio && <ViewRow label="О себе" value={profile.bio} />}
        {profile.interests.length > 0 && (
          <div className="view-row"><label>Интересы</label>
            <div className="chips">{profile.interests.map((i) => <span key={i} className="chip">{i}</span>)}</div></div>
        )}
        {serverError && <div className="error">{serverError}</div>}
        {saved && <div className="ok">Анкета сохранена</div>}
        <button className="btn wide" onClick={startEditing} data-testid="edit">Редактировать</button>
        <button className="link" onClick={toggleHidden}>{hidden ? "Показать анкету в ленте" : "Скрыть анкету из ленты"}</button>
        <button className="link danger" onClick={deleteProfile} data-testid="delete">
          {confirmDelete ? "Нажмите еще раз: данные будут удалены безвозвратно" : "Удалить учетную запись"}</button>
      </div>
    );
  }

  return (
    <div className="screen editor">
      <h2 className="title">Редактирование анкеты</h2>
      <label>Фотографии (до 6)</label>
      <PhotoUploader api={api} photos={photos} onChange={photosChanged} />

      <div className="field"><label>Имя</label>
        <input value={form.displayName} maxLength={64} onChange={(e) => set("displayName", e.target.value)} data-testid="name" />
        {errors.displayName && <div className="error">{errors.displayName}</div>}</div>
      <div className="field"><label>Дата рождения</label>
        <input type="date" value={form.birthDate} onChange={(e) => set("birthDate", e.target.value)} data-testid="birth" />
        {errors.birthDate && <div className="error">{errors.birthDate}</div>}</div>
      <div className="field"><label>Пол</label>
        <select value={form.gender} onChange={(e) => set("gender", e.target.value as Gender)} data-testid="gender">
          <option value="">—</option><option value="MALE">Мужской</option><option value="FEMALE">Женский</option>
        </select>
        {errors.gender && <div className="error">{errors.gender}</div>}</div>
      <div className="field"><label>Ищу</label>
        <select value={form.preferredGender} onChange={(e) => set("preferredGender", e.target.value as GenderPreference)} data-testid="pref">
          <option value="ANY">Всех</option><option value="FEMALE">Женщин</option><option value="MALE">Мужчин</option>
        </select></div>
      <div className="field"><label>Возраст от</label>
        <input type="number" min={18} max={99} value={form.minAge} onChange={(e) => set("minAge", Number(e.target.value))} /></div>
      <div className="field"><label>Возраст до</label>
        <input type="number" min={18} max={99} value={form.maxAge} onChange={(e) => set("maxAge", Number(e.target.value))} /></div>
      {errors.ageRange && <div className="error">{errors.ageRange}</div>}
      <label>Город</label>
      <input value={form.city} maxLength={64} onChange={(e) => set("city", e.target.value)} data-testid="city" />
      <label>О себе ({form.bio.length}/{MAX_BIO})</label>
      <textarea value={form.bio} rows={3} onChange={(e) => set("bio", e.target.value)} data-testid="bio" />
      {errors.bio && <div className="error">{errors.bio}</div>}
      <label>Интересы</label>
      <InterestSelector all={all} selected={form.interests} onChange={(s) => set("interests", s)} />

      {serverError && <div className="error">{serverError}</div>}
      {saved && <div className="ok">Анкета сохранена</div>}
      {!tg.hasMainButton && <button className="btn wide" disabled={busy} onClick={save} data-testid="save">Сохранить</button>}
      <button className="link" onClick={cancelEditing} data-testid="cancel-edit">Отмена</button>
    </div>
  );
}

function ViewRow({ label, value, testId }: { label: string; value: string; testId?: string }) {
  return <div className="view-row"><label>{label}</label><div className="view-value" data-testid={testId}>{value}</div></div>;
}
