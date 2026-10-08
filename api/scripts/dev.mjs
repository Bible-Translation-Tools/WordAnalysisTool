#!/usr/bin/env node
/**
 * Local dev runner: starts `wrangler dev --test-scheduled` and then fires the
 * cron handler on a loop, since wrangler never runs cron triggers by itself.
 * Ticks are sequential (the next one starts only after the previous handler
 * returned), so a long ingestion is never run twice at once.
 *
 *   npm run dev                      # 8787, tick every 10s
 *   TICK_SECONDS=5 npm run dev -- --port 8790
 *
 * Extra arguments are passed through to wrangler.
 */
import { spawn } from "node:child_process";

const args = process.argv.slice(2);
const portIdx = args.findIndex((a) => a === "--port" || a === "-p");
const port =
  portIdx !== -1 ? Number(args[portIdx + 1]) : Number(process.env.PORT || 8787);
const tickSeconds = Number(process.env.TICK_SECONDS || 10);
const base = `http://localhost:${port}`;
const scheduledUrl = `${base}/__scheduled?cron=*+*+*+*+*`;

const wrangler = spawn(
  "npx",
  ["wrangler", "dev", "--test-scheduled", ...args],
  { stdio: "inherit", shell: process.platform === "win32" },
);

let stopped = false;
const stop = () => {
  if (stopped) return;
  stopped = true;
  wrangler.kill("SIGINT");
};
process.on("SIGINT", stop);
process.on("SIGTERM", stop);
wrangler.on("exit", (code) => {
  stopped = true;
  process.exit(code ?? 0);
});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const log = (msg) => console.log(`[tick] ${msg}`);

async function waitForServer() {
  while (!stopped) {
    try {
      await fetch(base, { method: "HEAD" });
      return;
    } catch {
      await sleep(1000);
    }
  }
}

async function tick() {
  const started = Date.now();
  try {
    const res = await fetch(scheduledUrl);
    const seconds = ((Date.now() - started) / 1000).toFixed(1);
    log(`scheduled -> ${res.status} (${seconds}s)`);
  } catch (e) {
    log(`scheduled failed: ${e.message ?? e}`);
  }
}

await waitForServer();
log(`firing cron every ${tickSeconds}s on ${scheduledUrl}`);
while (!stopped) {
  await tick();
  await sleep(tickSeconds * 1000);
}
