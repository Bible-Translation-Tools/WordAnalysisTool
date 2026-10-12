import { sql, SQL } from "drizzle-orm";

/**
 * Consensus of a word's votes, as an aggregate over its `models` rows. Mirrors
 * `classifyVotes` in services/consensus.ts and feeds `words.consensus`:
 * NULL while any vote is unchecked (-1) or no models exist; 'none' when every
 * vote is processed but none is a valid 0/1.
 */
export function consensusSql(): SQL<string | null> {
  return sql<string | null>`
    CASE
      WHEN count(*) = 0 OR bool_or(status = -1) THEN NULL
      WHEN count(*) FILTER (WHERE status IN (0, 1)) = 0 THEN 'none'
      WHEN count(*) FILTER (WHERE status = 1) > count(*) FILTER (WHERE status = 0) THEN 'correct'
      WHEN count(*) FILTER (WHERE status = 0) > count(*) FILTER (WHERE status = 1) THEN 'incorrect'
      ELSE 'review'
    END
  `;
}

/** Every model voted the same way, and that way is 0 or 1. */
export function unanimousSql(): SQL<boolean> {
  return sql<boolean>`count(*) > 0 AND min(status) = max(status) AND min(status) IN (0, 1)`;
}
