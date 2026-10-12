import { and, asc, eq, exists, inArray, isNull, not } from "drizzle-orm";
import { MAX_PARAMS, PgDb } from "../client";
import { modelsTable, wordsTable } from "../schema";
import { WordsRepo } from "../../store";
import { chunkByParams } from "../../common/chunk";

export function createWordsRepo(db: PgDb): WordsRepo {
  return {
    async insertMany(words, batchId) {
      for (const chunk of chunkByParams(words, 5, MAX_PARAMS)) {
        await db
          .insert(wordsTable)
          .values(
            // Drizzle binds the boolean defaults as parameters: 5 per row.
            chunk.map((w) => ({
              word: w.word,
              verseId: w.verseId,
              batchId,
              unanimous: false,
              sampled: false,
            })),
          )
          .onConflictDoNothing({
            target: [wordsTable.word, wordsTable.batchId],
          });
      }
    },

    async fetchUnprocessedIds(words, batchId) {
      const wordIds: number[] = [];
      for (const chunk of chunkByParams(words, 1, MAX_PARAMS, 1)) {
        const rows = await db
          .select({ id: wordsTable.id })
          .from(wordsTable)
          .where(
            and(
              eq(wordsTable.batchId, batchId),
              inArray(
                wordsTable.word,
                chunk.map((w) => w.word),
              ),
              not(
                exists(
                  db
                    .select({ id: modelsTable.id })
                    .from(modelsTable)
                    .where(eq(modelsTable.wordId, wordsTable.id)),
                ),
              ),
            ),
          );
        wordIds.push(...rows.map((r) => r.id));
      }
      return wordIds;
    },

    async findIdByWord(batchId, word) {
      const [found] = await db
        .select({ id: wordsTable.id })
        .from(wordsTable)
        .where(and(eq(wordsTable.batchId, batchId), eq(wordsTable.word, word)))
        .limit(1);
      return found ? found.id : null;
    },

    findUnprocessedForBatch(batchId, limit) {
      // `consensus IS NULL` drives the scan through the partial index
      // idx_word_pending; the EXISTS keeps the exact "has an unchecked vote"
      // meaning (a word with no models at all is NULL too).
      return db.query.wordsTable.findMany({
        where: (words, { and, eq }) =>
          and(
            eq(words.batchId, batchId),
            isNull(words.consensus),
            exists(
              db
                .select({ id: modelsTable.id })
                .from(modelsTable)
                .where(
                  and(
                    eq(modelsTable.wordId, words.id),
                    eq(modelsTable.status, -1),
                  ),
                ),
            ),
          ),
        with: { models: true, verse: true },
        limit,
      });
    },

    findForReport(batchId) {
      return db.query.wordsTable.findMany({
        where: eq(wordsTable.batchId, batchId),
        with: {
          models: { orderBy: [asc(modelsTable.model)] },
          reviews: true,
          verse: true,
        },
        orderBy: [asc(wordsTable.word)],
      });
    },
  };
}
