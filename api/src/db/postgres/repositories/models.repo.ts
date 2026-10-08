import { and, eq, inArray, sql } from "drizzle-orm";
import { MAX_PARAMS, PgDb } from "../client";
import { modelsTable, wordsTable } from "../schema";
import { ModelsRepo } from "../../store";
import { chunkByParams } from "../../common/chunk";

export function createModelsRepo(db: PgDb): ModelsRepo {
  return {
    async seed(wordIds, models) {
      if (models.length === 0) return;
      // Drizzle binds column defaults (retries) as parameters too: 4 per row.
      const rows = wordIds.flatMap((wordId) =>
        models.map((model) => ({ model, status: -1, retries: 0, wordId })),
      );
      for (const chunk of chunkByParams(rows, 4, MAX_PARAMS)) {
        await db
          .insert(modelsTable)
          .values(chunk)
          .onConflictDoNothing({
            target: [modelsTable.model, modelsTable.wordId],
          });
      }
    },

    async updateResults(batchId, results) {
      for (const modelResult of results) {
        // Each word binds 3 params: CASE (word, status) + IN (word).
        for (const chunk of chunkByParams(modelResult.results, 3, MAX_PARAMS, 3)) {
          const statusCases = chunk.map(
            (r) =>
              sql`WHEN ${wordsTable.word} = ${r.word.trim()} THEN ${r.status}`,
          );
          const words = chunk.map((r) => r.word.trim());
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
                inArray(wordsTable.word, words),
              ),
            );
        }
      }
    },

    async deleteIncomplete(batchId) {
      const badWordIds = db
        .selectDistinct({ wordId: modelsTable.wordId })
        .from(modelsTable)
        .innerJoin(wordsTable, eq(modelsTable.wordId, wordsTable.id))
        .where(
          and(eq(wordsTable.batchId, batchId), eq(modelsTable.status, -1)),
        );
      await db.delete(modelsTable).where(inArray(modelsTable.wordId, badWordIds));
    },
  };
}
