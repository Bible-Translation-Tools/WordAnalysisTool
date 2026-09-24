import { and, eq, or, sql } from "drizzle-orm";
import { Database } from "../client";
import { versesTable } from "../schema";
import { SQL_BATCH_LIMIT } from "../../config/constants";
import { Verse } from "../../usfm";

export function createVersesRepo(db: Database) {
  return {
    /**
     * Insert verses for a resource, de-duplicating by (book, chapter, verse):
     * a single INSERT ... ON CONFLICT DO UPDATE cannot affect the same conflict
     * target twice, and some source USFM repeats a verse ref. Last occurrence
     * wins. Duplicates are logged so they can be found in the Cloudflare console.
     */
    async insertMany(verses: Verse[], resourceId: number): Promise<void> {
      const unique = new Map<string, Verse>();
      const duplicates: string[] = [];
      for (const v of verses) {
        const ref = `${v.book}:${v.chapter}:${v.verse}`;
        if (unique.has(ref)) duplicates.push(ref);
        unique.set(ref, v);
      }
      if (duplicates.length > 0) {
        console.error(
          `duplicate verse refs in resource ${resourceId} (${duplicates.length}): ${duplicates.join(", ")}`,
        );
      }
      const deduped = [...unique.values()];

      for (let i = 0; i < deduped.length; i += SQL_BATCH_LIMIT) {
        const batch = deduped.slice(i, i + SQL_BATCH_LIMIT);
        const values = batch.map((v) => ({
          bookCode: v.book,
          chapter: v.chapter,
          verse: v.verse,
          text: v.text,
          resourceId,
        }));

        if (values.length > 0) {
          await db
            .insert(versesTable)
            .values(values)
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
      }
    },

    async getByResource(resourceId: number): Promise<Verse[]> {
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

    /** Distinct book codes already stored for a resource. */
    async getStoredBookCodes(resourceId: number): Promise<string[]> {
      const rows = await db
        .selectDistinct({ book: versesTable.bookCode })
        .from(versesTable)
        .where(eq(versesTable.resourceId, resourceId));
      return rows.map((r) => r.book);
    },

    /**
     * Map "book:chapter:verse" -> verse text for a resource, limited to the given
     * refs. Fetches only what the caller needs (avoids loading a whole Bible).
     */
    async getTextsByRefs(
      resourceId: number,
      refs: { book: string; chapter: number; verse: string }[],
    ): Promise<Map<string, string>> {
      const map = new Map<string, string>();
      if (refs.length === 0) return map;

      const uniqueRefs = [
        ...new Map(
          refs.map((r) => [`${r.book}:${r.chapter}:${r.verse}`, r]),
        ).values(),
      ];

      const CHUNK = 200;
      for (let i = 0; i < uniqueRefs.length; i += CHUNK) {
        const slice = uniqueRefs.slice(i, i + CHUNK);
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
                ...slice.map((r) =>
                  and(
                    eq(versesTable.bookCode, r.book),
                    eq(versesTable.chapter, r.chapter),
                    eq(versesTable.verse, r.verse),
                  ),
                ),
              ),
            ),
          );

        for (const r of rows) {
          map.set(`${r.book}:${r.chapter}:${r.verse}`, r.text);
        }
      }
      return map;
    },

    /** Map "book:chapter:verse" -> verse id for a resource. */
    async getRefMap(resourceId: number): Promise<Map<string, number>> {
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
      for (const r of rows) {
        map.set(`${r.book}:${r.chapter}:${r.verse}`, r.id);
      }
      return map;
    },
  };
}

export type VersesRepo = ReturnType<typeof createVersesRepo>;
