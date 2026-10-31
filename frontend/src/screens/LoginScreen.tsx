import { useEffect, useState } from "react";
import type { ScreenProps } from "./Screen";
import type { AuthResponse } from "../types";
import { ApiError } from "../api/BackendApiClient";

/** Экран авторизации: при запуске отправляет initData на сервер. */
export function LoginScreen({ api, tg, onAuthorized }: ScreenProps & { onAuthorized: (r: AuthResponse) => Promise<void> }) {
  const [status, setStatus] = useState<"pending" | "error" | "outside">("pending");
  const [message, setMessage] = useState("");

  async function authorize() {
    if (!tg.available) { setStatus("outside"); return; }
    setStatus("pending");
    try {
      await onAuthorized(await api.authenticate(tg.initData));
    } catch (e) {
      setStatus("error");
      setMessage(e instanceof ApiError ? e.message : "Не удалось войти");
    }
  }

  useEffect(() => { authorize(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="center-screen">
      <div className="logo">♥</div>
      <h1>Знакомства</h1>
      {status === "pending" && <p>Входим через Telegram…</p>}
      {status === "outside" && <p>Откройте приложение из бота в Telegram — вход выполняется автоматически.</p>}
      {status === "error" && (<>
        <p className="error">{message}</p>
        <button className="btn" onClick={authorize}>Повторить</button>
      </>)}
    </div>
  );
}
