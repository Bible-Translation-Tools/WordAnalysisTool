import { and, eq, sql } from "drizzle-orm";
import { MAX_PARAMS, PgDb } from "../client";
import { versesTable } from "../schema";
import { VersesRepo } from "../../store";
import { chunkByParams } from "../../common/chunk";
import { dedupeVerses, uniqueRefs, verseKey } from "../../common/verses";

export function createVersesRepo(db: PgDb): VersesRepo {
  return {
    async insertMany(verses, resourceId) {
      const deduped = dedupeVerses(verses, resourceId);
      for (const chunk of chunkByParams(deduped, 5, MAX_PARAMS)) {
        await db
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
          });
      }
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
      // Row-value IN, not an OR chain: with a few dozen OR terms the planner
      // gives up on the unique index and scans every verse of the resource.
      // Each ref binds 3 params, plus the resource id.
      for (const chunk of chunkByParams(uniqueRefs(refs), 3, 600, 1)) {
        const tuples = sql.join(
          chunk.map((r) => sql`(${r.book}, ${r.chapter}, ${r.verse})`),
          sql`, `,
        );
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
              sql`(${versesTable.bookCode}, ${versesTable.chapter}, ${versesTable.verse}) IN (${tuples})`,
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
