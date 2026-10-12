import { and, eq, exists, inArray, not, sql } from "drizzle-orm";
import type { BatchItem } from "drizzle-orm/batch";
import { MAX_PARAMS, runBatch, SqliteDb } from "../client";
import { modelsTable, wordsTable } from "../schema";
import { ModelsRepo } from "../../store";
import { chunkByParams } from "../../common/chunk";
import { consensusSql, unanimousSql } from "../sql";
import { applyTallyDelta, countByConsensus, recountTallies } from "../tallies";
import { ConsensusCount, isZeroDelta, tallyDelta } from "../../common/tallies";

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

  /**
   * Votes changed, so the review pool drawn from them is stale; the next
   * review request redraws it. Hits only the partial `sampled` index.
   */
  const clearSampled = (batchId: string) =>
    db
      .update(wordsTable)
      .set({ sampled: false })
      .where(and(eq(wordsTable.batchId, batchId), sql`${wordsTable.sampled} = 1`));

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
      // The batch runs in order, so the refresh sees the new statuses. The
      // touched words' consensus is counted before and after inside the same
      // transaction; the difference is added to the batch's tally counters.
      const wordChunks = chunkByParams([...touched], 1, MAX_PARAMS, 1);
      const before = wordChunks.map((c) => countByConsensus(db, batchId, c));
      const after = wordChunks.map((c) => countByConsensus(db, batchId, c));
      const refresh = wordChunks.map((c) => refreshConsensus(batchId, c));
      const all: BatchItem<"sqlite">[] = [
        ...before,
        ...statements,
        ...refresh,
        ...(touched.size > 0 ? [clearSampled(batchId)] : []),
        ...after,
      ];
      const out = await runBatch(db, all);
      const n = wordChunks.length;
      const rows = (items: unknown[]) => items.flat() as ConsensusCount[];
      const delta = tallyDelta(
        rows(out.slice(0, n)),
        rows(out.slice(out.length - n)),
      );
      if (!isZeroDelta(delta)) await applyTallyDelta(db, batchId, delta);
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
        .set({ consensus: null, unanimous: false, sampled: false })
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
      // Pausing is rare; a full recount is simpler than tracking the delta.
      await recountTallies(db, batchId);
    },
  };
}
