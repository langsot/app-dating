import type { ProfileDto, ProfileForm } from "../types";

export const MAX_BIO = 500;
export const MAX_INTERESTS = 10;
export const MAX_PHOTOS = 6;

/** Возраст в полных годах на дату today. */
export function ageOn(birthDate: string, today: Date): number {
  const b = new Date(birthDate + "T00:00:00");
  if (Number.isNaN(b.getTime())) return NaN;
  let age = today.getFullYear() - b.getFullYear();
  const m = today.getMonth() - b.getMonth();
  if (m < 0 || (m === 0 && today.getDate() < b.getDate())) age--;
  return age;
}

/** Проверка формы анкеты на стороне клиента. Возвращает ошибки по полям. */
export function validateProfile(f: ProfileForm, today: Date = new Date()): Record<string, string> {
  const e: Record<string, string> = {};
  if (!f.displayName.trim()) e.displayName = "Укажите имя";
  else if (f.displayName.trim().length > 64) e.displayName = "Не более 64 символов";
  if (!f.birthDate) e.birthDate = "Укажите дату рождения";
  else {
    const age = ageOn(f.birthDate, today);
    if (Number.isNaN(age)) e.birthDate = "Некорректная дата";
    else if (age < 18) e.birthDate = "Сервис доступен с 18 лет";
    else if (age > 99) e.birthDate = "Проверьте дату рождения";
  }
  if (!f.gender) e.gender = "Укажите пол";
  if (f.bio.length > MAX_BIO) e.bio = `Не более ${MAX_BIO} символов`;
  if (f.minAge < 18 || f.maxAge > 99 || f.minAge > f.maxAge) e.ageRange = "Некорректный диапазон возраста";
  if (f.interests.length > MAX_INTERESTS) e.interests = `Не более ${MAX_INTERESTS} интересов`;
  return e;
}

export function emptyForm(firstName = ""): ProfileForm {
  return { displayName: firstName, birthDate: "", gender: "", bio: "", city: "",
    preferredGender: "ANY", minAge: 18, maxAge: 45, interests: [] };
}

export function formFromProfile(p: ProfileDto): ProfileForm {
  return { displayName: p.displayName, birthDate: p.birthDate, gender: p.gender, bio: p.bio ?? "",
    city: p.city ?? "", preferredGender: p.preferredGender, minAge: p.minAge, maxAge: p.maxAge,
    interests: [...p.interests] };
}

/** Переключение интереса с учетом ограничения на количество. */
export function toggleInterest(selected: string[], name: string): string[] {
  if (selected.includes(name)) return selected.filter((n) => n !== name);
  if (selected.length >= MAX_INTERESTS) return selected;
  return [...selected, name];
}

/** Поля, которые проверяются на каждом шаге регистрации. */
const STEP_FIELDS: string[][] = [["displayName", "birthDate"], ["gender"], ["ageRange"], ["bio"], ["interests"], []];

/** Ошибки только тех полей, что заполняются на данном шаге регистрации. */
export function stepErrors(f: ProfileForm, step: number, today: Date = new Date()): Record<string, string> {
  const all = validateProfile(f, today);
  return Object.fromEntries(Object.entries(all).filter(([k]) => STEP_FIELDS[step]?.includes(k)));
}
