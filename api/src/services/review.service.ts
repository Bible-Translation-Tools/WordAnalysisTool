import { Repositories } from "../db";
import { StatusCount, StatusLimit } from "../db/store";
import { BatchProgress, WordResponse } from "../types";
import { emptyProgress } from "./stats.service";

// Size of the review pool drawn from a batch's "good" (unanimous 0/1) words.
export const REVIEW_POOL_LIMIT = 370;

/**
 * Split `poolLimit` across statuses proportionally to how many good words
 * each has, rounding and then fixing the sum on the first status. Returns
 * null when the pool fits as a whole (no sampling needed).
 */
export function computeReviewLimits(
  counts: StatusCount[],
  poolLimit: number = REVIEW_POOL_LIMIT,
): StatusLimit[] | null {
  const total = counts.reduce((sum, row) => sum + row.count, 0);
  if (total <= poolLimit) return null;

  const limits = counts.map((c) => ({
    status: c.status,
    limit: Math.round((c.count / total) * poolLimit),
  }));

  const summed = limits.reduce((sum, c) => sum + c.limit, 0);
  if (summed !== poolLimit && limits.length > 0) {
    limits[0].limit += poolLimit - summed;
  }
  return limits;
}

/**
 * The review pool for a batch/user. The pool is drawn once per batch: good
 * (unanimous correct/incorrect) words, sampled proportionally down to
 * REVIEW_POOL_LIMIT and flagged `sampled`; later requests read just those
 * rows. The flag is cleared when the batch's votes change, which redraws the
 * pool on the next request. Returns the ordered pool + review progress; the
 * client walks it one word at a time.
 */
export async function sampleReviewWords(
  repos: Pick<Repositories, "reviews">,
  batchId: string,
  userId: number,
): Promise<{ output: WordResponse[]; progress: BatchProgress }> {
  if ((await repos.reviews.countSampled(batchId)) === 0) {
    const counts = await repos.reviews.countGoodWordsByStatus(batchId);
    await repos.reviews.markSampled(batchId, computeReviewLimits(counts));
  }
  const rows = await repos.reviews.fetchPool(batchId, userId);

  const output: WordResponse[] = rows.map((row) => ({
    word: row.word,
    ref: `${row.book}:${row.chapter}:${row.verse}`,
    text: row.text,
    correct: row.review ? row.review.correct : null,
    results: [],
  }));

  const progress: BatchProgress = {
    ...emptyProgress,
    reviewed: output.filter((word) => word.correct !== null).length,
    total: output.length,
  };

  return { output, progress };
}
