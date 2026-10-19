import { and, eq, exists, inArray, not, sql } from "drizzle-orm";
import { MAX_PARAMS, PgDb } from "../client";
import { modelsTable, wordsTable } from "../schema";
import { ModelsRepo } from "../../store";
import { chunkByParams } from "../../common/chunk";
import { consensusSql, unanimousSql } from "../sql";
import { applyTallyDelta, countByConsensus, recountTallies } from "../tallies";
import { isZeroDelta, tallyDelta } from "../../common/tallies";

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

  /**
   * Votes changed, so the review pool drawn from them is stale; the next
   * review request redraws it. Hits only the partial `sampled` index.
   */
  const clearSampled = (batchId: string) =>
    db
      .update(wordsTable)
      .set({ sampled: false })
      .where(and(eq(wordsTable.batchId, batchId), sql`${wordsTable.sampled} = true`));

  /** Words of the batch with an unchecked vote are back to "not processed". */
  const resetUnchecked = (batchId: string) =>
    db
      .update(wordsTable)
      .set({ consensus: null, unanimous: false, sampled: false })
      .where(
        and(
          eq(wordsTable.batchId, batchId),
          exists(
            db
              .select({ id: modelsTable.id })
              .from(modelsTable)
              .where(and(eq(modelsTable.wordId, wordsTable.id), eq(modelsTable.status, -1))),
          ),
        ),
      );

  return {
    async seed(batchId, wordIds, models) {
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
      await resetUnchecked(batchId);
    },

    async updateResults(batchId, results) {
      const touched = new Set<string>();
      for (const modelResult of results) {
        for (const r of modelResult.results) touched.add(r.word.trim());
      }
      const wordChunks = chunkByParams([...touched], 1, MAX_PARAMS, 1);
      // Count the touched words' consensus before and after; the difference
      // is added to the batch's tally counters.
      const before = (await Promise.all(
        wordChunks.map((c) => countByConsensus(db, batchId, c)),
      )).flat();
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
      for (const chunk of wordChunks) await refreshConsensus(batchId, chunk);
      if (touched.size > 0) await clearSampled(batchId);
      const after = (await Promise.all(
        wordChunks.map((c) => countByConsensus(db, batchId, c)),
      )).flat();
      const delta = tallyDelta(before, after);
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
