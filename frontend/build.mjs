// Сборка клиента: TypeScript/TSX -> один модуль JS в статические ресурсы Spring Boot.
import * as esbuild from "esbuild";
import { cpSync, mkdirSync } from "node:fs";

const out = "../backend/src/main/resources/static";
const watch = process.argv.includes("--watch");
const test = process.argv.includes("--test");

if (test) {
  // Отдельная сборка логических модулей для модульных тестов (node --test).
  await esbuild.build({
    entryPoints: ["src/logic/index.ts"], bundle: true, format: "esm", platform: "node",
    outfile: "test/build/logic.mjs", target: "node18",
  });
} else {
  mkdirSync(out + "/assets", { recursive: true });
  cpSync("index.html", out + "/index.html");
  cpSync("src/styles.css", out + "/assets/styles.css");
  const ctx = await esbuild.context({
    entryPoints: ["src/main.tsx"], bundle: true, format: "esm", minify: !watch, sourcemap: watch,
    jsx: "automatic", target: "es2020", outfile: out + "/assets/app.js",
    define: { "process.env.NODE_ENV": watch ? '"development"' : '"production"' },
  });
  if (watch) { await ctx.watch(); console.log("watching..."); }
  else { await ctx.rebuild(); await ctx.dispose(); console.log("built ->", out); }
}
