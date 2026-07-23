import { sql, SQL } from "drizzle-orm";

/**
 * Canonical consensus of a set of model votes for one word. The rule is shared
 * by the CSV report (JS array form, `classifyVotes`) and the stats aggregation
 * (SQL form, `consensusSql`) so it lives in exactly one place.
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

/**
 * Aggregate consensus expression over a `models` group (grouped by word_id).
 * Mirrors `classifyVotes`. Words with any unchecked (-1) vote, or no valid
 * 0/1 vote, resolve to NULL (excluded from the stats tallies).
 */
export function consensusSql(): SQL<string> {
  return sql<string>`
    CASE
      WHEN bool_or(status = -1) THEN NULL
      WHEN count(*) FILTER (WHERE status IN (0, 1)) = 0 THEN NULL
      WHEN count(*) FILTER (WHERE status = 1) > count(*) FILTER (WHERE status = 0) THEN 'Correct'
      WHEN count(*) FILTER (WHERE status = 0) > count(*) FILTER (WHERE status = 1) THEN 'Incorrect'
      ELSE 'Review Needed'
    END
  `;
}

/** True when every model for the word has been processed (no -1 left). */
export function isProcessedSql(): SQL<boolean> {
  return sql<boolean>`NOT bool_or(status = -1)`;
}
