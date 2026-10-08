import { DbDriver, Repositories } from "./store";
import { createPostgresDb } from "./postgres/client";
import { createPostgresRepositories } from "./postgres/repositories";
import { createD1Db } from "./sqlite/client";
import { D1UsageMeter } from "./sqlite/metrics";
import { createSqliteRepositories } from "./sqlite/repositories";

export type { Repositories } from "./store";

const DRIVERS: readonly DbDriver[] = ["postgres", "d1"];

/** The driver selected by `DB_DRIVER` (defaults to postgres). */
export function resolveDriver(env: CloudflareBindings): DbDriver {
  const driver = env.DB_DRIVER || "postgres";
  if (!DRIVERS.includes(driver as DbDriver)) {
    throw new Error(
      `unknown DB_DRIVER "${driver}" (expected one of: ${DRIVERS.join(", ")})`,
    );
  }
  return driver as DbDriver;
}

/**
 * Build the repositories for the configured driver:
 *  - `postgres`: Supabase/Postgres via `DATABASE_URL` (default)
 *  - `d1`: the Cloudflare D1 binding `DB` (rows read/written metered when
 *    `options.d1Meter` is given; see sqlite/metrics.ts)
 */
export function createRepositories(
  env: CloudflareBindings,
  options: { d1Meter?: D1UsageMeter } = {},
): Repositories {
  switch (resolveDriver(env)) {
    case "d1": {
      if (!env.DB) throw new Error("DB_DRIVER=d1 but no D1 binding `DB`");
      return createSqliteRepositories(createD1Db(env.DB, options.d1Meter));
    }
    case "postgres": {
      if (!env.DATABASE_URL) {
        throw new Error("DB_DRIVER=postgres but DATABASE_URL is not set");
      }
      return createPostgresRepositories(createPostgresDb(env.DATABASE_URL));
    }
  }
}
