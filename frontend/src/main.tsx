import { createRoot } from "react-dom/client";
import { App } from "./App";
import { TelegramWebAppAdapter } from "./telegram/TelegramWebAppAdapter";

const tg = new TelegramWebAppAdapter();
tg.ready();
tg.expand();
createRoot(document.getElementById("root")!).render(<App />);
