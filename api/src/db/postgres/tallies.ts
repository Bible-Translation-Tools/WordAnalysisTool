import { and, count, eq, inArray, sql } from "drizzle-orm";
import { PgDb } from "./client";
import { batchesTable, wordsTable } from "./schema";
import { BatchTallies } from "../store";
import { ConsensusCount, TallyDelta, talliesOf } from "../common/tallies";

/** GROUP BY consensus over a batch's words, optionally only the named ones. */
export function countByConsensus(db: PgDb, batchId: string, words?: string[]) {
  return db
    .select({ consensus: wordsTable.consensus, count: count() })
    .from(wordsTable)
    .where(
      words
        ? and(eq(wordsTable.batchId, batchId), inArray(wordsTable.word, words))
        : eq(wordsTable.batchId, batchId),
    )
    .groupBy(wordsTable.consensus);
}

/** Add a delta to the batch's counters (one row written). */
export function applyTallyDelta(db: PgDb, batchId: string, d: TallyDelta) {
  return db
    .update(batchesTable)
    .set({
      wordsCompleted: sql`${batchesTable.wordsCompleted} + ${d.completed}`,
      wordsCorrect: sql`${batchesTable.wordsCorrect} + ${d.correct}`,
      wordsIncorrect: sql`${batchesTable.wordsIncorrect} + ${d.incorrect}`,
      wordsReview: sql`${batchesTable.wordsReview} + ${d.reviewNeeded}`,
    })
    .where(eq(batchesTable.id, batchId));
}

/** Full recount from `words`, stored on the batch. */
export async function recountTallies(db: PgDb, batchId: string): Promise<BatchTallies> {
  const rows: ConsensusCount[] = await countByConsensus(db, batchId);
  const t = talliesOf(rows);
  await db
    .update(batchesTable)
    .set({
      wordsTotal: t.total,
      wordsCompleted: t.completed,
      wordsCorrect: t.correct,
      wordsIncorrect: t.incorrect,
      wordsReview: t.reviewNeeded,
    })
    .where(eq(batchesTable.id, batchId));
  return t;
}
