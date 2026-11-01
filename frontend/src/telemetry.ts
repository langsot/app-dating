import { faro, getWebInstrumentations, initializeFaro, type TransportItem } from "@grafana/faro-web-sdk";

// Адрес сбора задается при сборке; без него телеметрия выключена
declare const FARO_URL: string;

/**
 * Telegram передает подписанные данные входа в адресе страницы (#tgWebAppData=...):
 * они не должны покидать приложение ни в метаданных, ни в текстах ошибок.
 */
export function stripTelegramData<T>(item: T): T {
  return JSON.parse(JSON.stringify(item).replace(/#?tgWebApp[^"\s\\]*/g, ""));
}

/** Запускает отправку ошибок и логов клиента в Grafana. Сбой телеметрии не должен мешать приложению. */
export function initTelemetry(): void {
  if (!FARO_URL) return;
  try {
    initializeFaro({
      url: FARO_URL,
      app: { name: "app-dating", environment: "production" },
      instrumentations: getWebInstrumentations({ captureConsole: true }),
      beforeSend: (item: TransportItem) => stripTelegramData(item),
    });
  } catch (e) {
    console.warn("[telemetry] не запущена", e);
  }
}

/** Привязывает события к пользователю. Передается только внутренний идентификатор. */
export function setTelemetryUser(userId: number): void {
  try {
    faro.api?.setUser({ id: String(userId) });
  } catch (e) {
    console.warn("[telemetry] пользователь не задан", e);
  }
}
