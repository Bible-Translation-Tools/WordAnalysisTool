import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { Repositories } from "../../src/db/store";

export type DriverFixture = {
  repos: Repositories;
  close: () => Promise<void>;
};

/** Postgres repositories over an in-memory PGlite, migrated with the real migrations. */
export async function makePostgres(): Promise<DriverFixture> {
  const { PGlite } = await import("@electric-sql/pglite");
  const { drizzle } = await import("drizzle-orm/pglite");
  const { migrate } = await import("drizzle-orm/pglite/migrator");
  const schema = await import("../../src/db/postgres/schema");
  const { createPostgresRepositories } = await import(
    "../../src/db/postgres/repositories"
  );

  const client = new PGlite();
  const db = drizzle(client, { schema });
  await migrate(db, { migrationsFolder: join(__dirname, "../../drizzle/postgres") });
  return {
    repos: createPostgresRepositories(db),
    close: () => client.close(),
  };
}

/** SQLite repositories over an in-memory libsql, migrated with the D1 migrations. */
export async function makeSqlite(): Promise<DriverFixture> {
  const { createClient } = await import("@libsql/client");
  const { drizzle } = await import("drizzle-orm/libsql");
  const schema = await import("../../src/db/sqlite/schema");
  const { createSqliteRepositories } = await import(
    "../../src/db/sqlite/repositories"
  );

  const client = createClient({ url: ":memory:" });
  // D1 enforces foreign keys; plain SQLite does not by default.
  await client.execute("PRAGMA foreign_keys = ON");

  // D1 allows at most 100 bound parameters per statement. libsql does not
  // care, so enforce it here: a statement that would fail on D1 fails the test.
  const D1_MAX_PARAMS = 100;
  const assertParams = (stmt: unknown) => {
    const args = typeof stmt === "object" && stmt && "args" in stmt ? stmt.args : [];
    const n = Array.isArray(args) ? args.length : Object.keys(args ?? {}).length;
    if (n > D1_MAX_PARAMS) {
      const sql = typeof stmt === "object" && stmt && "sql" in stmt ? String(stmt.sql) : "";
      throw new Error(
        `statement binds ${n} params (D1 max ${D1_MAX_PARAMS}): ${sql.slice(0, 120)}...`,
      );
    }
  };
  const guarded = new Proxy(client, {
    get(target, prop, receiver) {
      if (prop === "execute") {
        return (stmt: unknown, ...rest: unknown[]) => {
          assertParams(stmt);
          return (target.execute as any)(stmt, ...rest);
        };
      }
      if (prop === "batch") {
        return (stmts: unknown[], ...rest: unknown[]) => {
          stmts.forEach(assertParams);
          return (target.batch as any)(stmts, ...rest);
        };
      }
      return Reflect.get(target, prop, receiver);
    },
  });

  const dir = join(__dirname, "../../drizzle/sqlite");
  const files = readdirSync(dir).filter((f) => f.endsWith(".sql")).sort();
  for (const file of files) {
    const statements = readFileSync(join(dir, file), "utf8")
      .split("--> statement-breakpoint")
      .map((s) => s.trim())
      .filter(Boolean);
    for (const statement of statements) await client.execute(statement);
  }

  const db = drizzle(guarded, { schema });
  return {
    repos: createSqliteRepositories(db),
    close: async () => client.close(),
  };
}

export const DRIVERS: [string, () => Promise<DriverFixture>][] = [
  ["postgres", makePostgres],
  ["sqlite", makeSqlite],
];
