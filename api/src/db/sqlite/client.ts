import { drizzle } from "drizzle-orm/d1";
import type { BaseSQLiteDatabase } from "drizzle-orm/sqlite-core";
import type { BatchItem, BatchResponse } from "drizzle-orm/batch";
import * as schema from "./schema";
import { D1UsageMeter, meterD1 } from "./metrics";

/**
 * Any async Drizzle SQLite database over our schema that supports `batch`.
 * The worker uses D1; tests use libsql in memory. Repositories are written
 * against this.
 */
export type SqliteDb = BaseSQLiteDatabase<"async", any, typeof schema> & {
  batch<U extends BatchItem<"sqlite">, T extends Readonly<[U, ...U[]]>>(
    batch: T,
  ): Promise<BatchResponse<T>>;
};

/** Create a Drizzle client over a D1 binding, metered when `meter` is given. */
export function createD1Db(d1: D1Database, meter?: D1UsageMeter): SqliteDb {
  return drizzle(meter ? meterD1(d1, meter) : d1, { schema });
}

/**
 * D1 binds at most 100 parameters per statement
 * (https://developers.cloudflare.com/d1/platform/limits/), so bulk writes are
 * split into many small statements and sent together with `db.batch`.
 */
export const MAX_PARAMS = 100;

/** Statements per `db.batch` round trip. */
const BATCH_STATEMENTS = 100;

/** Run statements in batches: one round trip (and one transaction) per group. */
export async function runBatch(
  db: SqliteDb,
  statements: BatchItem<"sqlite">[],
): Promise<void> {
  for (let i = 0; i < statements.length; i += BATCH_STATEMENTS) {
    const group = statements.slice(i, i + BATCH_STATEMENTS);
    if (group.length === 1) {
      await group[0];
    } else if (group.length > 1) {
      await db.batch(group as [BatchItem<"sqlite">, ...BatchItem<"sqlite">[]]);
    }
  }
}
