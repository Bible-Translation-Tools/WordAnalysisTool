/**
 * Canonical consensus of a set of model votes for one word. The rule is shared
 * by the CSV report (this JS form) and the stats aggregation (the SQL form,
 * `consensusSql` in each driver's `src/db/<driver>/sql.ts`), which must agree.
 *
 * Only 0 (incorrect) and 1 (correct) count as votes; any other value (e.g. a
 * legacy 2) is ignored. Majority wins; a tie is "review"; no valid vote at all
 * is "none".
 */
export type Consensus = "correct" | "incorrect" | "review" | "none";

export function classifyVotes(statuses: number[]): Consensus {
  const correct = statuses.filter((s) => s === 1).length;
  const incorrect = statuses.filter((s) => s === 0).length;
  if (correct === 0 && incorrect === 0) return "none";
  if (correct > incorrect) return "correct";
  if (incorrect > correct) return "incorrect";
  return "review";
}

/** Human-facing labels used in the CSV report. */
export const REPORT_LABEL: Record<Consensus, string> = {
  correct: "Likely Correct",
  incorrect: "Likely Incorrect",
  review: "Review Needed",
  none: "Not Processed",
};
