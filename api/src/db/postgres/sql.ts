import { AnyColumn, sql, SQL } from "drizzle-orm";

/**
 * Aggregate consensus expression over a `models` group (grouped by word_id).
 * Mirrors `classifyVotes` in services/consensus.ts. Words with any unchecked
 * (-1) vote, or no valid 0/1 vote, resolve to NULL (excluded from tallies).
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

/**
 * Review sampling order: a hash of (word id, user id), so each status quota is
 * drawn from across the whole vocabulary instead of the lowest word ids, which
 * follow ingestion order and so are alphabetical.
 */
export function samplingOrder(wordId: AnyColumn, userId: number): SQL {
  return sql`md5(${wordId}::text || ':' || ${userId}::text)`;
}
