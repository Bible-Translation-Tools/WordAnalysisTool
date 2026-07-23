import { Repositories } from "../db/repositories";
import { BatchEntity } from "../db/repositories/batches.repo";
import { getBooksForTranslation } from "../integrations/biel";
import { parseVerses, findSingletons } from "../usfm";
import { briefReason } from "../lib/utils";
import { BATCH_MAX_RETRIES } from "../config/constants";
import { BatchError } from "../types";

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/**
 * Fetch a USFM file, retrying on transient failures (the content host throttles
 * bursts, so a timed-out / 429 / 5xx book usually succeeds on retry).
 */
export async function fetchUsfm(url: string, attempts = 4): Promise<string> {
  let lastReason = "unknown error";
  for (let i = 0; i < attempts; i++) {
    try {
      const res = await fetch(url, {
        headers: { "User-Agent": "btt-writer-android" },
      });
      if (res.ok) return await res.text();
      lastReason = `status ${res.status}`;
      if (res.status >= 400 && res.status < 500 && res.status !== 429) break;
    } catch (e: any) {
      lastReason = e?.message || String(e);
    }
    if (i < attempts - 1) await sleep(500 * (i + 1));
  }
  throw new Error(lastReason);
}

export function createIngestionService(repos: Repositories) {
  /**
   * Download and store any missing books of a translation into `resourceId`.
   * Idempotent per book, so retries make forward progress. Throws if any book
   * fails — callers must not proceed on a partial set.
   */
  async function ingestResource(
    ietfCode: string,
    resourceType: string,
    resourceId: number,
  ): Promise<void> {
    const contents = await getBooksForTranslation(ietfCode, resourceType);
    const usable = contents.filter((c) => c.url);
    if (usable.length === 0) {
      throw new Error(`no USFM content for ${ietfCode}/${resourceType}`);
    }

    const storedBooks = new Set(
      await repos.verses.getStoredBookCodes(resourceId),
    );
    const failures: string[] = [];

    for (const content of usable) {
      const slug = content.bookSlug?.toLowerCase();
      if (slug && storedBooks.has(slug)) continue;

      const label = content.bookSlug ?? content.url ?? "?";
      try {
        const usfm = await fetchUsfm(content.url!);
        const bookVerses = parseVerses(usfm, content.bookSlug ?? undefined);
        if (bookVerses.length > 0) {
          await repos.verses.insertMany(bookVerses, resourceId);
          storedBooks.add(bookVerses[0].book.toLowerCase());
        }
      } catch (e: any) {
        const reason = briefReason(e);
        console.error(
          `failed to ingest book ${label} for ${ietfCode}/${resourceType}: ${reason}`,
        );
        failures.push(`${label}: ${reason}`);
      }
    }

    if (failures.length > 0) {
      throw new Error(
        `${failures.length} of ${usable.length} books failed for ${ietfCode}/${resourceType} — ${failures
          .slice(0, 10)
          .join("; ")}`,
      );
    }
  }

  /**
   * Source ingestion for one batch: ensure the translation's source and the
   * reference source are both fully stored, then compute singleton words (linked
   * to their verse) and queue the batch for AI processing.
   */
  async function ingestSource(batch: BatchEntity): Promise<void> {
    const batchId = batch.id;

    try {
      if (!batch.resourceId) throw new Error("batch has no resource");
      const resourceId = batch.resourceId;

      const models: string[] = batch.models ? JSON.parse(batch.models) : [];
      if (models.length === 0) throw new Error("batch has no models");

      // 1. The batch's own translation source.
      const target = await repos.resources.getRef(resourceId);
      if (!target) throw new Error("batch resource not found");
      await ingestResource(target.ietf, target.resourceType, resourceId);

      // 2. The reference source (shared across batches; only missing books are
      //    downloaded). Skipped if the batch has no reference.
      if (batch.refResourceId) {
        const ref = await repos.resources.getRef(batch.refResourceId);
        if (!ref) throw new Error("reference resource not found");
        await ingestResource(ref.ietf, ref.resourceType, batch.refResourceId);
      }

      // 3. Compute singletons from the complete source; link each to its verse.
      const verses = await repos.verses.getByResource(resourceId);
      const singletons = findSingletons(verses, batch.apostropheIsSeparator);
      if (singletons.length === 0) throw new Error("no singleton words found");

      const verseRefMap = await repos.verses.getRefMap(resourceId);
      const wordRows = singletons
        .map((s) => ({ word: s.word, verseId: verseRefMap.get(s.ref) }))
        .filter(
          (w): w is { word: string; verseId: number } =>
            w.verseId !== undefined,
        );

      await repos.words.insertMany(wordRows, batchId);
      const wordIds = await repos.words.fetchUnprocessedIds(wordRows, batchId);
      await repos.models.seed(wordIds, models);

      await repos.batches.updateById(batchId, {
        ingesting: false,
        pending: false, // Set to true if you want to queue the batch for processing after ingestion
        error: null,
        retries: 0,
        updatedAt: new Date(),
      });
    } catch (error: any) {
      console.error("ingestion error:", error);

      const errorDetails: BatchError = {
        message: `ingestion error: ${briefReason(error)}`,
        prompt: null,
        model: null,
        response: null,
      };

      const toUpdate: Record<string, unknown> = {
        error: JSON.stringify(errorDetails),
        updatedAt: new Date(),
      };

      const retries = batch.retries + 1;
      if (retries >= BATCH_MAX_RETRIES) {
        toUpdate.ingesting = false;
        toUpdate.pending = false;
        toUpdate.retries = 0;
      } else {
        toUpdate.retries = retries;
      }

      await repos.batches.updateById(batchId, toUpdate);
    }
  }

  return { ingestResource, ingestSource };
}

export type IngestionService = ReturnType<typeof createIngestionService>;
