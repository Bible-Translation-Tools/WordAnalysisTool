import { AnyColumn, sql, SQL } from "drizzle-orm";

/**
 * Aggregate consensus expression over a `models` group (grouped by word_id).
 * Mirrors `classifyVotes` in services/consensus.ts. Words with any unchecked
 * (-1) vote, or no valid 0/1 vote, resolve to NULL (excluded from tallies).
 * SQLite has no bool_or; `max(cond)` over 0/1 comparisons is the same thing.
 */
export function consensusSql(): SQL<string> {
  return sql<string>`
    CASE
      WHEN max(status = -1) THEN NULL
      WHEN count(*) FILTER (WHERE status IN (0, 1)) = 0 THEN NULL
      WHEN count(*) FILTER (WHERE status = 1) > count(*) FILTER (WHERE status = 0) THEN 'Correct'
      WHEN count(*) FILTER (WHERE status = 0) > count(*) FILTER (WHERE status = 1) THEN 'Incorrect'
      ELSE 'Review Needed'
    END
  `;
}

/** True (1) when every model for the word has been processed (no -1 left). */
export function isProcessedSql(): SQL<boolean> {
  return sql<boolean>`NOT max(status = -1)`;
}

/**
 * Review sampling order: a per-user pseudo-random permutation of word ids, so
 * each status quota is drawn from across the whole vocabulary instead of the
 * lowest word ids (ingestion order, i.e. alphabetical). SQLite has no md5, so
 * this is a Knuth multiplicative hash on (word id + user id) modulo 2^32.
 */
export function samplingOrder(wordId: AnyColumn, userId: number): SQL {
  return sql`(((${wordId} + ${userId}) * 2654435761) % 4294967296)`;
}
