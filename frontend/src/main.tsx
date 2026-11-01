import { createRoot } from "react-dom/client";
import { initTelemetry } from "./telemetry";
import { App } from "./App";
import { TelegramWebAppAdapter } from "./telegram/TelegramWebAppAdapter";

initTelemetry();
const tg = new TelegramWebAppAdapter();
tg.ready();
tg.expand();
createRoot(document.getElementById("root")!).render(<App />);
