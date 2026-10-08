import { drizzle } from "drizzle-orm/postgres-js";
import type { PgDatabase } from "drizzle-orm/pg-core";
import postgres from "postgres";
import * as schema from "./schema";

/**
 * Any Drizzle Postgres database over our schema. The worker uses postgres-js
 * (Supabase pooler); tests use PGlite. Repositories are written against this.
 */
export type PgDb = PgDatabase<any, typeof schema>;

/** Create a Drizzle client bound to the Postgres connection string. */
export function createPostgresDb(databaseUrl: string): PgDb {
  const client = postgres(databaseUrl, {
    // DATABASE_URL is Supabase's transaction pooler, which doesn't support
    // prepared statements.
    prepare: false,
    // The schema has no custom/array types; skip the type-lookup round trip.
    fetch_types: false,
  });
  return drizzle(client, { schema });
}

/** Postgres allows 65535 bound parameters; stay well under it. */
export const MAX_PARAMS = 30000;
