import { count, eq, sql } from "drizzle-orm";
import { PgDb } from "../client";
import { wordReviewsTable, wordsTable } from "../schema";
import { StatsRepo } from "../../store";

export function createStatsRepo(db: PgDb): StatsRepo {
  return {
    async getBatchTallies(batchId) {
      // One pass over the batch's words; consensus is maintained on write.
      const [stats] = await db
        .select({
          correct: count(sql`CASE WHEN ${wordsTable.consensus} = 'correct' THEN 1 END`),
          incorrect: count(sql`CASE WHEN ${wordsTable.consensus} = 'incorrect' THEN 1 END`),
          reviewNeeded: count(sql`CASE WHEN ${wordsTable.consensus} = 'review' THEN 1 END`),
          total: count(),
          completed: count(wordsTable.consensus),
        })
        .from(wordsTable)
        .where(eq(wordsTable.batchId, batchId));
      return stats;
    },

    getReviewCountsByUser(batchId) {
      return db
        .select({ userId: wordReviewsTable.userId, count: count() })
        .from(wordReviewsTable)
        .innerJoin(wordsTable, eq(wordReviewsTable.wordId, wordsTable.id))
        .where(eq(wordsTable.batchId, batchId))
        .groupBy(wordReviewsTable.userId);
    },
  };
}
