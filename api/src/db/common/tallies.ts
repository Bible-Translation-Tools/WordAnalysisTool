import { BatchTallies, Consensus } from "../store";

/** `words.consensus` counts for a set of words, as returned by GROUP BY. */
export type ConsensusCount = { consensus: Consensus | null; count: number };

/** Change in the tallies between two consensus counts of the same words. */
export type TallyDelta = Omit<BatchTallies, "total">;

export const ZERO_TALLIES: BatchTallies = {
  correct: 0,
  incorrect: 0,
  reviewNeeded: 0,
  total: 0,
  completed: 0,
};

/** Fold GROUP BY consensus rows into tallies (`total` = all rows). */
export function talliesOf(rows: ConsensusCount[]): BatchTallies {
  const t = { ...ZERO_TALLIES };
  for (const { consensus, count } of rows) {
    t.total += count;
    if (consensus === null) continue;
    t.completed += count;
    if (consensus === "correct") t.correct += count;
    else if (consensus === "incorrect") t.incorrect += count;
    else if (consensus === "review") t.reviewNeeded += count;
  }
  return t;
}

/** `after - before` for the words whose consensus may have changed. */
export function tallyDelta(before: ConsensusCount[], after: ConsensusCount[]): TallyDelta {
  const b = talliesOf(before);
  const a = talliesOf(after);
  return {
    correct: a.correct - b.correct,
    incorrect: a.incorrect - b.incorrect,
    reviewNeeded: a.reviewNeeded - b.reviewNeeded,
    completed: a.completed - b.completed,
  };
}

export const isZeroDelta = (d: TallyDelta) =>
  d.correct === 0 && d.incorrect === 0 && d.reviewNeeded === 0 && d.completed === 0;
