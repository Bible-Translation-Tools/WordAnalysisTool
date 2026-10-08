import { config } from "dotenv";
import { defineConfig } from "drizzle-kit";

config({ path: ".dev.vars" });

/** Postgres (Supabase) migrations. Apply with `npm run db:pg:migrate`. */
export default defineConfig({
  schema: "./src/db/postgres/schema.ts",
  out: "./drizzle/postgres",
  dialect: "postgresql",
  dbCredentials: {
    url: process.env.DATABASE_URL!,
  },
  schemaFilter: ["public"],
  tablesFilter: ["*"],
});
