import { and, asc, eq, exists, inArray, not } from "drizzle-orm";
import { Database } from "../client";
import { modelsTable, wordsTable } from "../schema";
import { SQL_BATCH_LIMIT } from "../../config/constants";

export function createWordsRepo(db: Database) {
  return {
    async insertMany(
      words: { word: string; verseId: number }[],
      batchId: string,
    ): Promise<void> {
      for (let i = 0; i < words.length; i += SQL_BATCH_LIMIT) {
        const batch = words.slice(i, i + SQL_BATCH_LIMIT);
        const wordValues = batch.map((word) => ({
          word: word.word,
          verseId: word.verseId,
          batchId,
        }));

        if (wordValues.length > 0) {
          await db
            .insert(wordsTable)
            .values(wordValues)
            .onConflictDoNothing({
              target: [wordsTable.word, wordsTable.batchId],
            });
        }
      }
    },

    /** Ids of words in a batch that have no model rows yet (need processing). */
    async fetchUnprocessedIds(
      words: { word: string }[],
      batchId: string,
    ): Promise<number[]> {
      const wordIds: number[] = [];
      for (let i = 0; i < words.length; i += SQL_BATCH_LIMIT) {
        const batch = words.slice(i, i + SQL_BATCH_LIMIT);
        if (batch.length > 0) {
          const result = await db
            .select({ id: wordsTable.id })
            .from(wordsTable)
            .where(
              and(
                eq(wordsTable.batchId, batchId),
                inArray(
                  wordsTable.word,
                  batch.map((w) => w.word),
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
          wordIds.push(...result.map((row) => row.id));
        }
      }
      return wordIds;
    },

    /** Resolve (batchId, word[]) to their ids. */
    async findIdsByWords(
      batchId: string,
      words: string[],
    ): Promise<Map<string, number>> {
      const found = await db
        .select({ id: wordsTable.id, word: wordsTable.word })
        .from(wordsTable)
        .where(
          and(eq(wordsTable.batchId, batchId), inArray(wordsTable.word, words)),
        );
      return new Map(found.map((row) => [row.word, row.id]));
    },

    /**
     * Words in a batch that still have at least one unchecked (status -1) model,
     * with their models and verse. Used by the AI-processing loop.
     */
    findUnprocessedForBatch(batchId: string, limit: number) {
      return db.query.wordsTable.findMany({
        where: (words, { and, eq }) =>
          and(
            eq(words.batchId, batchId),
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

    /** All words in a batch with models, reviews and verse — for CSV report. */
    findForReport(batchId: string) {
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

export type WordsRepo = ReturnType<typeof createWordsRepo>;
