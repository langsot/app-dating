import { useEffect, useState } from "react";
import type { ScreenProps } from "./Screen";
import type { Gender, GenderPreference, InterestDto, PhotoDto, ProfileDto, ProfileForm } from "../types";
import { emptyForm, formFromProfile, MAX_BIO, stepErrors } from "../logic/profileForm";
import { ApiError } from "../api/BackendApiClient";
import { PhotoUploader } from "../components/PhotoUploader";
import { InterestSelector } from "../components/InterestSelector";

const TITLES = ["Как вас зовут?", "Ваш пол", "Кого вы ищете?", "Немного о себе", "Ваши интересы", "Добавьте фото"];
const PHOTO_STEP = TITLES.length - 1;
/** Шаги, которые можно пропустить. */
const OPTIONAL = [3, 4];

/** Пошаговая регистрация. Анкета сохраняется перед шагом с фото: сервер принимает фото только для готовой анкеты. */
export function OnboardingScreen({ api, tg, profile, onSaved, onDone }:
  ScreenProps & { profile: ProfileDto | null; onSaved: (p: ProfileDto) => void; onDone: () => void }) {
  const [step, setStep] = useState(profile ? PHOTO_STEP : 0);
  const [form, setForm] = useState<ProfileForm>(profile ? formFromProfile(profile) : emptyForm(tg.firstName));
  const [photos, setPhotos] = useState<PhotoDto[]>(profile?.photos ?? []);
  const [saved, setSaved] = useState(profile);
  const [all, setAll] = useState<InterestDto[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => { api.getInterests().then(setAll).catch(() => setAll([])); }, [api]);

  const set = <K extends keyof ProfileForm>(k: K, v: ProfileForm[K]) => setForm({ ...form, [k]: v });

  async function next() {
    if (busy) return;
    const e = stepErrors(form, step);
    setErrors(e); setServerError("");
    if (Object.keys(e).length) { tg.haptic("error"); return; }
    if (step === PHOTO_STEP) { onDone(); return; }
    if (step < PHOTO_STEP - 1) { setStep(step + 1); return; }
    setBusy(true); tg.setMainButtonProgress(true);
    try {
      const p = { ...(await api.saveProfile(form)), photos, complete: photos.length > 0 };
      setSaved(p);
      onSaved(p);
      setStep(PHOTO_STEP);
    } catch (err) {
      setServerError(err instanceof ApiError ? [err.message, ...err.details].join("; ") : "Не удалось сохранить анкету");
      tg.haptic("error");
    } finally { setBusy(false); tg.setMainButtonProgress(false); }
  }

  function photosChanged(p: PhotoDto[]) {
    setPhotos(p);
    if (saved) onSaved({ ...saved, photos: p, complete: p.length > 0 });
  }

  const canProceed = step !== PHOTO_STEP || photos.length > 0;
  const nextLabel = step === PHOTO_STEP ? "Готово" : "Далее";

  useEffect(() => {
    if (canProceed && tg.hasMainButton) tg.showMainButton(nextLabel.toUpperCase(), next);
    return () => tg.hideMainButton();
  });

  return (
    <div className="screen wizard" data-testid="onboarding">
      <div className="wizard-progress" data-testid="wizard-step">Шаг {step + 1} из {TITLES.length}</div>
      <div className="wizard-bar"><span style={{ width: `${((step + 1) / TITLES.length) * 100}%` }} /></div>
      <h2 className="title">{TITLES[step]}</h2>

      {step === 0 && (<>
        <div className="field"><label>Имя</label>
          <input value={form.displayName} maxLength={64} onChange={(e) => set("displayName", e.target.value)} data-testid="name" />
          {errors.displayName && <div className="error">{errors.displayName}</div>}</div>
        <div className="field"><label>Дата рождения</label>
          <input type="date" value={form.birthDate} onChange={(e) => set("birthDate", e.target.value)} data-testid="birth" />
          {errors.birthDate && <div className="error">{errors.birthDate}</div>}</div>
      </>)}

      {step === 1 && (
        <div className="field"><label>Пол</label>
          <select value={form.gender} onChange={(e) => set("gender", e.target.value as Gender)} data-testid="gender">
            <option value="">—</option><option value="MALE">Мужской</option><option value="FEMALE">Женский</option>
          </select>
          {errors.gender && <div className="error">{errors.gender}</div>}</div>
      )}

      {step === 2 && (<>
        <div className="field"><label>Показывать</label>
          <select value={form.preferredGender} onChange={(e) => set("preferredGender", e.target.value as GenderPreference)} data-testid="pref">
            <option value="ANY">Всех</option><option value="FEMALE">Женщин</option><option value="MALE">Мужчин</option>
          </select></div>
        <div className="field"><label>Возраст от</label>
          <input type="number" min={18} max={99} value={form.minAge} onChange={(e) => set("minAge", Number(e.target.value))} data-testid="min-age" /></div>
        <div className="field"><label>Возраст до</label>
          <input type="number" min={18} max={99} value={form.maxAge} onChange={(e) => set("maxAge", Number(e.target.value))} data-testid="max-age" /></div>
        {errors.ageRange && <div className="error">{errors.ageRange}</div>}
      </>)}

      {step === 3 && (<>
        <label>Город</label>
        <input value={form.city} maxLength={64} onChange={(e) => set("city", e.target.value)} data-testid="city" />
        <label>О себе ({form.bio.length}/{MAX_BIO})</label>
        <textarea value={form.bio} rows={4} onChange={(e) => set("bio", e.target.value)} data-testid="bio" />
        {errors.bio && <div className="error">{errors.bio}</div>}
      </>)}

      {step === 4 && <InterestSelector all={all} selected={form.interests} onChange={(s) => set("interests", s)} />}

      {step === PHOTO_STEP && (<>
        <p className="hint">Нужна хотя бы одна фотография, чтобы открыть ленту и появиться в ней.</p>
        <PhotoUploader api={api} photos={photos} onChange={photosChanged} />
      </>)}

      {serverError && <div className="error">{serverError}</div>}
      {!tg.hasMainButton && canProceed && (
        <button className="btn wide" disabled={busy} onClick={next} data-testid="wizard-next">{nextLabel}</button>
      )}
      {OPTIONAL.includes(step) && <div className="hint">Этот шаг можно пропустить и заполнить позже в анкете.</div>}
      {step > 0 && (
        <button className="link" onClick={() => { setErrors({}); setServerError(""); setStep(step - 1); }} data-testid="wizard-back">Назад</button>
      )}
    </div>
  );
}
