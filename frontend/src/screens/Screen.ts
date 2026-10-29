import type { BackendApiClient } from "../api/BackendApiClient";
import type { TelegramWebAppAdapter } from "../telegram/TelegramWebAppAdapter";

/**
 * Общий контракт экранов (абстрактный класс Screen модели УПД-3).
 * В React экран — функциональный компонент, получающий клиент API и адаптер Telegram.
 */
export interface ScreenProps {
  api: BackendApiClient;
  tg: TelegramWebAppAdapter;
}
