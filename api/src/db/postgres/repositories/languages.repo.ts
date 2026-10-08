import { eq, sql } from "drizzle-orm";
import { PgDb } from "../client";
import { languagesTable } from "../schema";
import { LanguagesRepo } from "../../store";

export function createLanguagesRepo(db: PgDb): LanguagesRepo {
  return {
    async upsert(info) {
      const [row] = await db
        .insert(languagesTable)
        .values({
          code: info.ietfCode,
          name: info.nationalName,
          angName: info.englishName,
          direction: info.direction,
          gateway: false,
        })
        .onConflictDoUpdate({
          target: languagesTable.code,
          set: {
            name: sql`excluded.ln`,
            angName: sql`excluded.ang`,
            direction: sql`excluded.ld`,
          },
        })
        .returning({ id: languagesTable.id });
      return row.id;
    },

    async getName(languageId) {
      const [row] = await db
        .select({ name: languagesTable.angName })
        .from(languagesTable)
        .where(eq(languagesTable.id, languageId));
      return row?.name ?? "";
    },
  };
}
