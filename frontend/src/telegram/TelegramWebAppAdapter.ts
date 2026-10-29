// Адаптер объекта window.Telegram.WebApp: остальной код не обращается к API Telegram напрямую.
export interface ThemeParams { [key: string]: string | undefined; }

interface TgMainButton {
  setText(t: string): void; show(): void; hide(): void; enable(): void; disable(): void;
  showProgress(leaveActive?: boolean): void; hideProgress(): void;
  onClick(cb: () => void): void; offClick(cb: () => void): void;
}
interface TgWebApp {
  initData: string;
  initDataUnsafe?: { user?: { id: number; first_name?: string; username?: string } };
  themeParams: ThemeParams; colorScheme?: string;
  ready(): void; expand(): void; close(): void;
  MainButton: TgMainButton;
  HapticFeedback?: { impactOccurred(s: string): void; notificationOccurred(t: string): void };
  openTelegramLink(url: string): void;
}
declare global { interface Window { Telegram?: { WebApp?: TgWebApp } } }

export class TelegramWebAppAdapter {
  private readonly webApp: TgWebApp | undefined;
  private mainButtonHandler: (() => void) | null = null;

  constructor(win: Window = window) {
    this.webApp = win.Telegram?.WebApp;
  }

  /** Запущено ли приложение внутри Telegram (есть подписанные данные). */
  get available(): boolean { return !!this.webApp && !!this.webApp.initData; }
  get initData(): string { return this.webApp?.initData ?? ""; }
  get themeParams(): ThemeParams { return this.webApp?.themeParams ?? {}; }
  get firstName(): string { return this.webApp?.initDataUnsafe?.user?.first_name ?? ""; }

  ready(): void { this.webApp?.ready(); }
  expand(): void { this.webApp?.expand(); }

  showMainButton(text: string, cb: () => void): void {
    const mb = this.webApp?.MainButton;
    if (!mb) return;
    if (this.mainButtonHandler) mb.offClick(this.mainButtonHandler);
    this.mainButtonHandler = cb;
    mb.setText(text); mb.onClick(cb); mb.enable(); mb.show();
  }

  setMainButtonProgress(on: boolean): void {
    const mb = this.webApp?.MainButton;
    if (!mb) return;
    if (on) mb.showProgress(false); else mb.hideProgress();
  }

  hideMainButton(): void {
    const mb = this.webApp?.MainButton;
    if (!mb) return;
    if (this.mainButtonHandler) mb.offClick(this.mainButtonHandler);
    this.mainButtonHandler = null;
    mb.hide();
  }

  /** Есть ли у Telegram системная кнопка (вне Telegram используется обычная кнопка формы). */
  get hasMainButton(): boolean { return !!this.webApp?.MainButton && this.available; }

  haptic(type: "light" | "medium" | "success" | "error"): void {
    const h = this.webApp?.HapticFeedback;
    if (!h) return;
    if (type === "success" || type === "error") h.notificationOccurred(type);
    else h.impactOccurred(type);
  }

  openTelegramLink(url: string): void {
    if (this.webApp) this.webApp.openTelegramLink(url);
    else window.open(url, "_blank");
  }
}
