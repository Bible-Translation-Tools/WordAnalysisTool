import { and, count, eq, exists } from "drizzle-orm";
import { SqliteDb } from "../client";
import { batchesTable, wordReviewsTable, wordsTable } from "../schema";
import { StatsRepo } from "../../store";
import { ZERO_TALLIES } from "../../common/tallies";
import { recountTallies } from "../tallies";

export function createStatsRepo(db: SqliteDb): StatsRepo {
  return {
    async getBatchTallies(batchId) {
      // Counters maintained on the batch row: one row read.
      const [b] = await db
        .select({
          correct: batchesTable.wordsCorrect,
          incorrect: batchesTable.wordsIncorrect,
          reviewNeeded: batchesTable.wordsReview,
          total: batchesTable.wordsTotal,
          completed: batchesTable.wordsCompleted,
        })
        .from(batchesTable)
        .where(eq(batchesTable.id, batchId));
      return b ?? { ...ZERO_TALLIES };
    },

    recountTallies(batchId) {
      return recountTallies(db, batchId);
    },

    getReviewCountsByUser(batchId) {
      // Correlated EXISTS instead of a join: the planner would otherwise loop
      // over every word of the batch and probe reviews; this loops over the
      // (few) reviews and probes words by primary key.
      return db
        .select({ userId: wordReviewsTable.userId, count: count() })
        .from(wordReviewsTable)
        .where(
          exists(
            db
              .select({ id: wordsTable.id })
              .from(wordsTable)
              .where(
                and(
                  eq(wordsTable.id, wordReviewsTable.wordId),
                  eq(wordsTable.batchId, batchId),
                ),
              ),
          ),
        )
        .groupBy(wordReviewsTable.userId);
    },
  };
}
