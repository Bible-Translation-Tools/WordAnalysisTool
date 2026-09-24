import { BatchError, SplitBatchJson } from "../types";

export const isAdmin = (username: string, env: CloudflareBindings) => {
  const admins = env.WAT_ADMINS.split(",");
  return admins.includes(username);
};

export const chunkArray = (array: any[], size: number) => {
  const arr = [];
  for (var i = 0; i < array.length; i += size) {
    arr.push(array.slice(i, i + size));
  }
  return arr;
};

/**
 * Keep error reasons short and useful. Drizzle wraps the driver error, so its
 * `.message` is just the "Failed query" SQL dump — the real Postgres message is
 * on `.cause`. Prefer that, take the first line (drops the params dump that
 * embeds verse text), and cap the length.
 */
export const briefReason = (e: any): string => {
  const msg = e?.cause?.message ?? e?.message ?? String(e);
  return String(msg).split("\n")[0].slice(0, 200);
};

export const isChatError = (obj: any): obj is BatchError => {
  return !!(
    obj &&
    typeof obj.message === "string" &&
    (typeof obj.prompt === "string" || obj.prompt === null) &&
    (typeof obj.model === "string" || obj.model === null) &&
    (typeof obj.response === "string" || obj.response === null)
  );
};

/**
 * The models a batch was configured with. Written as a JSON array, but rows
 * predating that are plain comma separated names, so both are read.
 */
export const parseModels = (stored: string | null): string[] => {
  if (!stored) return [];

  const trimmed = stored.trim();
  if (!trimmed) return [];

  if (trimmed.startsWith("[")) {
    try {
      const parsed = JSON.parse(trimmed);
      if (Array.isArray(parsed)) {
        return parsed.filter((m): m is string => typeof m === "string");
      }
    } catch {
      // fall through to the comma separated reading
    }
  }

  return trimmed
    .split(",")
    .map((model) => model.trim().replace(/^"|"$/g, ""))
    .filter((model) => model.length > 0);
};
