import { and, eq, exists, inArray, not, sql } from "drizzle-orm";
import type { BatchItem } from "drizzle-orm/batch";
import { MAX_PARAMS, runBatch, SqliteDb } from "../client";
import { modelsTable, wordsTable } from "../schema";
import { ModelsRepo } from "../../store";
import { chunkByParams } from "../../common/chunk";
import { consensusSql, unanimousSql } from "../sql";

export function createModelsRepo(db: SqliteDb): ModelsRepo {
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
      const statements = chunkByParams(rows, 4, MAX_PARAMS).map((chunk) =>
        db
          .insert(modelsTable)
          .values(chunk)
          .onConflictDoNothing({
            target: [modelsTable.model, modelsTable.wordId],
          }),
      );
      await runBatch(db, statements);
    },

    async updateResults(batchId, results) {
      const touched = new Set<string>();
      const statements: BatchItem<"sqlite">[] = results.flatMap((modelResult) =>
        // Each word binds 3 params: CASE (word, status) + IN (word); plus
        // retries, model and batch id.
        chunkByParams(modelResult.results, 3, MAX_PARAMS, 3).map((chunk) => {
          const statusCases = chunk.map(
            (r) =>
              sql`WHEN ${wordsTable.word} = ${r.word.trim()} THEN ${r.status}`,
          );
          const words = chunk.map((r) => r.word.trim());
          words.forEach((w) => touched.add(w));
          const statusFragment = sql.join(statusCases, sql` `);
          return db
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
        }),
      );
      // The batch runs in order, so the refresh sees the new statuses.
      for (const chunk of chunkByParams([...touched], 1, MAX_PARAMS, 1)) {
        statements.push(refreshConsensus(batchId, chunk));
      }
      await runBatch(db, statements);
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
