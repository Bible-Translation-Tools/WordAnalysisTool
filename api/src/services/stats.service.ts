import { count, eq, sql } from "drizzle-orm";
import { Database } from "../db/client";
import { modelsTable, wordReviewsTable, wordsTable } from "../db/schema";
import { BatchProgress, BatchStatus } from "../types";
import { consensusSql, isProcessedSql } from "./consensus";

export const emptyProgress: BatchProgress = {
  correct: 0,
  incorrect: 0,
  review_needed: 0,
  reviewed: 0,
  completed: 0,
  total: 0,
};

/** Consensus tallies + review progress for a batch. */
export async function computeBatchProgress(
  db: Database,
  batchId: string,
): Promise<BatchProgress> {
  const consensusSubquery = db
    .select({
      wordId: modelsTable.wordId,
      consensus: consensusSql().as("consensus"),
      isProcessed: isProcessedSql().as("is_processed"),
    })
    .from(modelsTable)
    .groupBy(modelsTable.wordId)
    .as("consensus_subquery");

  const [stats] = await db
    .select({
      correct: count(sql`CASE WHEN consensus = 'Correct' THEN 1 END`),
      incorrect: count(sql`CASE WHEN consensus = 'Incorrect' THEN 1 END`),
      reviewNeeded: count(sql`CASE WHEN consensus = 'Review Needed' THEN 1 END`),
      total: count(wordsTable.id),
      completed: count(sql`CASE WHEN is_processed THEN 1 END`),
    })
    .from(wordsTable)
    .leftJoin(consensusSubquery, eq(wordsTable.id, consensusSubquery.wordId))
    .where(eq(wordsTable.batchId, batchId));

  const userReviewCounts = await db
    .select({ userId: wordReviewsTable.userId, count: count() })
    .from(wordReviewsTable)
    .innerJoin(wordsTable, eq(wordReviewsTable.wordId, wordsTable.id))
    .where(eq(wordsTable.batchId, batchId))
    .groupBy(wordReviewsTable.userId);

  const totalReviews = userReviewCounts.reduce((sum, row) => sum + row.count, 0);
  const averageReviews =
    userReviewCounts.length > 0 ? totalReviews / userReviewCounts.length : 0;

  return {
    correct: stats.correct,
    incorrect: stats.incorrect,
    review_needed: stats.reviewNeeded,
    reviewed: Math.round(averageReviews),
    completed: stats.completed,
    total: stats.total,
  };
}

/** Derive the client-facing batch status from progress + batch flags. */
export function deriveStatus(
  progress: BatchProgress,
  flags: { ingesting: boolean; pending: boolean },
): BatchStatus {
  const p = progress.total > 0 ? progress.completed / progress.total : 1;

  let status: BatchStatus;
  if (p === 0) status = BatchStatus.QUEUED;
  else if (p === 1) status = BatchStatus.COMPLETE;
  else status = BatchStatus.RUNNING;

  if (flags.ingesting) {
    // Still preparing the source — keep the client polling + spinner running.
    status = BatchStatus.QUEUED;
  } else if (!flags.pending) {
    status = BatchStatus.COMPLETE;
  }
  return status;
}
