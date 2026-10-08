import { and, eq, exists, inArray, not, sql } from "drizzle-orm";
import { MAX_PARAMS, PgDb } from "../client";
import { modelsTable, wordsTable } from "../schema";
import { ModelsRepo } from "../../store";
import { chunkByParams } from "../../common/chunk";
import { consensusSql, unanimousSql } from "../sql";

export function createModelsRepo(db: PgDb): ModelsRepo {
  /** Recompute `consensus` / `unanimous` for the named words from their votes. */
  const refreshConsensus = (batchId: string, words: string[]) =>
    db
      .update(wordsTable)
      .set({
        consensus: sql`(select ${consensusSql()} from ${modelsTable} where ${modelsTable.wordId} = ${wordsTable.id})`,
        unanimous: sql`coalesce((select ${unanimousSql()} from ${modelsTable} where ${modelsTable.wordId} = ${wordsTable.id}), false)`,
      })
      .where(
        and(eq(wordsTable.batchId, batchId), inArray(wordsTable.word, words)),
      );

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
      const touched = new Set<string>();
      for (const modelResult of results) {
        // Each word binds 3 params: CASE (word, status) + IN (word).
        for (const chunk of chunkByParams(modelResult.results, 3, MAX_PARAMS, 3)) {
          const statusCases = chunk.map(
            (r) =>
              sql`WHEN ${wordsTable.word} = ${r.word.trim()} THEN ${r.status}`,
          );
          const words = chunk.map((r) => r.word.trim());
          words.forEach((w) => touched.add(w));
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
      for (const chunk of chunkByParams([...touched], 1, MAX_PARAMS, 1)) {
        await refreshConsensus(batchId, chunk);
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
      // Words left without any model row are back to "not processed".
      await db
        .update(wordsTable)
        .set({ consensus: null, unanimous: false })
        .where(
          and(
            eq(wordsTable.batchId, batchId),
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
    },
  };
}
