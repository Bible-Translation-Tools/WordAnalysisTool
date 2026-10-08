import { and, eq, or, sql } from "drizzle-orm";
import { MAX_PARAMS, runBatch, SqliteDb } from "../client";
import { versesTable } from "../schema";
import { VersesRepo } from "../../store";
import { chunkByParams } from "../../common/chunk";
import { dedupeVerses, uniqueRefs, verseKey } from "../../common/verses";

export function createVersesRepo(db: SqliteDb): VersesRepo {
  return {
    async insertMany(verses, resourceId) {
      const deduped = dedupeVerses(verses, resourceId);
      const statements = chunkByParams(deduped, 5, MAX_PARAMS).map((chunk) =>
        db
          .insert(versesTable)
          .values(
            chunk.map((v) => ({
              bookCode: v.book,
              chapter: v.chapter,
              verse: v.verse,
              text: v.text,
              resourceId,
            })),
          )
          .onConflictDoUpdate({
            target: [
              versesTable.bookCode,
              versesTable.chapter,
              versesTable.verse,
              versesTable.resourceId,
            ],
            set: { text: sql`excluded.text` },
          }),
      );
      await runBatch(db, statements);
    },

    getByResource(resourceId) {
      return db
        .select({
          book: versesTable.bookCode,
          chapter: versesTable.chapter,
          verse: versesTable.verse,
          text: versesTable.text,
        })
        .from(versesTable)
        .where(eq(versesTable.resourceId, resourceId));
    },

    async getStoredBookCodes(resourceId) {
      const rows = await db
        .selectDistinct({ book: versesTable.bookCode })
        .from(versesTable)
        .where(eq(versesTable.resourceId, resourceId));
      return rows.map((r) => r.book);
    },

    async getTextsByRefs(resourceId, refs) {
      const map = new Map<string, string>();
      // Each ref binds 3 params, plus the resource id.
      for (const chunk of chunkByParams(uniqueRefs(refs), 3, MAX_PARAMS, 1)) {
        const rows = await db
          .select({
            book: versesTable.bookCode,
            chapter: versesTable.chapter,
            verse: versesTable.verse,
            text: versesTable.text,
          })
          .from(versesTable)
          .where(
            and(
              eq(versesTable.resourceId, resourceId),
              or(
                ...chunk.map((r) =>
                  and(
                    eq(versesTable.bookCode, r.book),
                    eq(versesTable.chapter, r.chapter),
                    eq(versesTable.verse, r.verse),
                  ),
                ),
              ),
            ),
          );
        for (const r of rows) map.set(verseKey(r), r.text);
      }
      return map;
    },

    async getRefMap(resourceId) {
      const rows = await db
        .select({
          id: versesTable.id,
          book: versesTable.bookCode,
          chapter: versesTable.chapter,
          verse: versesTable.verse,
        })
        .from(versesTable)
        .where(eq(versesTable.resourceId, resourceId));
      const map = new Map<string, number>();
      for (const r of rows) map.set(verseKey(r), r.id);
      return map;
    },
  };
}
