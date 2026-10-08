import { Repositories } from "../db";
import { BatchProgress, BatchStatus } from "../types";

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
  repos: Pick<Repositories, "stats">,
  batchId: string,
): Promise<BatchProgress> {
  const [tallies, userReviewCounts] = await Promise.all([
    repos.stats.getBatchTallies(batchId),
    repos.stats.getReviewCountsByUser(batchId),
  ]);

  const totalReviews = userReviewCounts.reduce((sum, row) => sum + row.count, 0);
  const averageReviews =
    userReviewCounts.length > 0 ? totalReviews / userReviewCounts.length : 0;

  return {
    correct: tallies.correct,
    incorrect: tallies.incorrect,
    review_needed: tallies.reviewNeeded,
    reviewed: Math.round(averageReviews),
    completed: tallies.completed,
    total: tallies.total,
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
