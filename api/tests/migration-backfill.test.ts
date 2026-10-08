import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * The migration that adds `words.consensus` / `words.unanimous` backfills them
 * from existing votes. Apply every earlier migration, insert legacy-shaped
 * rows, apply the last one, and check the derived columns.
 */

type Exec = (sql: string) => Promise<void>;
type Query = (sql: string) => Promise<Record<string, unknown>[]>;

function migrationFiles(dir: string): string[][] {
  return readdirSync(dir)
    .filter((f) => f.endsWith(".sql"))
    .sort()
    .map((f) =>
      readFileSync(join(dir, f), "utf8")
        .split("--> statement-breakpoint")
        .map((s) => s.trim())
        .filter(Boolean),
    );
}

async function runScenario(exec: Exec, query: Query, dir: string, q: (s: string) => string) {
  const files = migrationFiles(dir);
  const last = files[files.length - 1];
  for (const file of files.slice(0, -1)) for (const s of file) await exec(s);

  await exec(
    `INSERT INTO ${q("users")} (wacs_user_id, username, email) VALUES (1, 'u', 'u@example.com')`,
  );
  await exec(`INSERT INTO ${q("languages")} (lc, ln, ang, ld) VALUES ('xx', 'x', 'X', 'ltr')`);
  await exec(`INSERT INTO ${q("resources")} (resource_type, language_id) VALUES ('ulb', 1)`);
  await exec(
    `INSERT INTO ${q("verses")} (book_code, chapter, verse, text, resource_id) VALUES ('gen', 1, '1', 't', 1)`,
  );
  await exec(`INSERT INTO ${q("batches")} (id, user_id, resource_id) VALUES ('b', 1, 1)`);
  // w1 unanimous correct, w2 majority correct, w3 tie, w4 pending, w5 legacy only, w6 no models
  await exec(
    `INSERT INTO ${q("words")} (word, batch_id, verse_id) VALUES ('w1','b',1),('w2','b',1),('w3','b',1),('w4','b',1),('w5','b',1),('w6','b',1)`,
  );
  await exec(
    `INSERT INTO ${q("models")} (model, status, word_id) VALUES
      ('a',1,1),('b',1,1),
      ('a',1,2),('b',1,2),('c',0,2),
      ('a',1,3),('b',0,3),
      ('a',1,4),('b',-1,4),
      ('a',2,5)`,
  );

  for (const s of last) await exec(s);

  const rows = await query(
    `SELECT word, consensus, unanimous FROM ${q("words")} ORDER BY word`,
  );
  const truthy = (v: unknown) => v === true || v === 1;
  expect(rows.map((r) => [r.word, r.consensus, truthy(r.unanimous)])).toEqual([
    ["w1", "correct", true],
    ["w2", "correct", false],
    ["w3", "review", false],
    ["w4", null, false],
    ["w5", "none", false],
    ["w6", null, false],
  ]);
}

describe("consensus backfill migration", () => {
  it("postgres", async () => {
    const { PGlite } = await import("@electric-sql/pglite");
    const client = new PGlite();
    try {
      await runScenario(
        async (sql) => {
          await client.exec(sql);
        },
        async (sql) => (await client.query<Record<string, unknown>>(sql)).rows,
        join(__dirname, "../drizzle/postgres"),
        (t) => `"${t}"`,
      );
    } finally {
      await client.close();
    }
  }, 60_000);

  it("sqlite", async () => {
    const { createClient } = await import("@libsql/client");
    const client = createClient({ url: ":memory:" });
    try {
      await runScenario(
        async (sql) => {
          await client.execute(sql);
        },
        async (sql) => (await client.execute(sql)).rows as Record<string, unknown>[],
        join(__dirname, "../drizzle/sqlite"),
        (t) => `\`${t}\``,
      );
    } finally {
      client.close();
    }
  });
});
