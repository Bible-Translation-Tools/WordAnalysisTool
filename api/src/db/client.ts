import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import * as schema from "./schema";

/** Create a Drizzle client bound to the Supabase Postgres connection. */
export function createDb(env: CloudflareBindings) {
  const client = postgres(env.DATABASE_URL, {
    // DATABASE_URL is Supabase's transaction pooler, which doesn't support
    // prepared statements.
    prepare: false,
    // The schema has no custom/array types; skip the type-lookup round trip.
    fetch_types: false,
  });
  return drizzle(client, { schema });
}

export type Database = ReturnType<typeof createDb>;
