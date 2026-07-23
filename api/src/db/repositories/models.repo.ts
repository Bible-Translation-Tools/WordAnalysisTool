import { and, eq, inArray, isNull, lte, sql } from "drizzle-orm";
import { Database } from "../client";
import { modelsTable, wordsTable } from "../schema";
import { SQL_BATCH_LIMIT } from "../../config/constants";
import { BatchError, ModelResult } from "../../types";

export function createModelsRepo(db: Database) {
  return {
    /** Seed one status=-1 (unchecked) row per (word, model). */
    async seed(wordIds: number[], models: string[]): Promise<void> {
      const limit = Math.round(SQL_BATCH_LIMIT / models.length);
      for (let i = 0; i < wordIds.length; i += limit) {
        const wordIdBatch = wordIds.slice(i, i + limit);
        if (wordIdBatch.length > 0) {
          const modelValuesBatch = wordIdBatch.flatMap((wordId) =>
            models.map((model) => ({ model, status: -1, wordId })),
          );
          if (modelValuesBatch.length > 0) {
            await db
              .insert(modelsTable)
              .values(modelValuesBatch)
              .onConflictDoNothing({
                target: [modelsTable.model, modelsTable.wordId],
              });
          }
        }
      }
    },

    async updateResults(
      batchId: string,
      results: ModelResult[],
    ): Promise<BatchError | null> {
      for (const modelResult of results) {
        const statusCases: Array<ReturnType<typeof sql>> = [];
        const updatedWords: string[] = [];

        for (const result of modelResult.results) {
          const word = result.word.trim();
          statusCases.push(
            sql`WHEN ${wordsTable.word} = ${word} THEN ${result.status}`,
          );
          updatedWords.push(word);
        }

        if (statusCases.length > 0) {
          const statusFragment = sql.join(statusCases, sql` `);
          await db
            .update(modelsTable)
            .set({
              status: sql`CASE ${statusFragment} ELSE ${modelsTable.status} END`,
              retries: modelResult.retries,
            })
            .from(wordsTable)
            .where(
              and(
                eq(modelsTable.wordId, wordsTable.id),
                eq(modelsTable.model, modelResult.model),
                eq(wordsTable.batchId, batchId),
                inArray(wordsTable.word, updatedWords),
              ),
            );
        }
      }
      return null;
    },

    async getCompletedWordsCount(batchId: string): Promise<number> {
      const result = await db
        .select({ count: sql<number>`count(${wordsTable.id})` })
        .from(wordsTable)
        .leftJoin(
          modelsTable,
          and(
            eq(wordsTable.id, modelsTable.wordId),
            lte(modelsTable.status, -1),
          ),
        )
        .where(and(eq(wordsTable.batchId, batchId), isNull(modelsTable.id)));
      return result[0].count;
    },

    /** Delete model rows still unchecked (status -1) — used when pausing a batch. */
    async deleteIncomplete(): Promise<void> {
      const badWordIds = db
        .selectDistinct({ wordId: modelsTable.wordId })
        .from(modelsTable)
        .where(eq(modelsTable.status, -1));
      await db.delete(modelsTable).where(inArray(modelsTable.wordId, badWordIds));
    },
  };
}

export type ModelsRepo = ReturnType<typeof createModelsRepo>;
