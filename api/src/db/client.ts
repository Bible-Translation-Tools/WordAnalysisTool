import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import * as schema from "./schema";

/** Create a Drizzle client bound to the Supabase Postgres connection. */
export function createDb(env: CloudflareBindings) {
  const client = postgres(env.DATABASE_URL);
  return drizzle(client, { schema });
}

export type Database = ReturnType<typeof createDb>;
