import { count, eq, sql } from "drizzle-orm";
import { PgDb } from "../client";
import { modelsTable, wordReviewsTable, wordsTable } from "../schema";
import { StatsRepo } from "../../store";
import { consensusSql, isProcessedSql } from "../sql";

export function createStatsRepo(db: PgDb): StatsRepo {
  return {
    async getBatchTallies(batchId) {
      const consensus = db
        .select({
          wordId: modelsTable.wordId,
          consensus: consensusSql().as("consensus"),
          isProcessed: isProcessedSql().as("is_processed"),
        })
        .from(modelsTable)
        .innerJoin(wordsTable, eq(modelsTable.wordId, wordsTable.id))
        .where(eq(wordsTable.batchId, batchId))
        .groupBy(modelsTable.wordId)
        .as("consensus_subquery");

      const [stats] = await db
        .select({
          correct: count(sql`CASE WHEN consensus = 'Correct' THEN 1 END`),
          incorrect: count(sql`CASE WHEN consensus = 'Incorrect' THEN 1 END`),
          reviewNeeded: count(
            sql`CASE WHEN consensus = 'Review Needed' THEN 1 END`,
          ),
          total: count(wordsTable.id),
          completed: count(sql`CASE WHEN is_processed THEN 1 END`),
        })
        .from(wordsTable)
        .leftJoin(consensus, eq(wordsTable.id, consensus.wordId))
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
