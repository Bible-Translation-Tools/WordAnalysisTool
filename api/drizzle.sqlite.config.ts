import { defineConfig } from "drizzle-kit";

/**
 * SQLite (Cloudflare D1) migrations. Only `generate` is used here; the SQL
 * files are applied by wrangler (`npm run db:d1:migrate:local|remote`), which
 * reads `migrations_dir` from wrangler.jsonc.
 */
export default defineConfig({
  schema: "./src/db/sqlite/schema.ts",
  out: "./drizzle/sqlite",
  dialect: "sqlite",
});
