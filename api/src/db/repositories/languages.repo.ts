import { eq, sql } from "drizzle-orm";
import { Database } from "../client";
import { languagesTable } from "../schema";
import { LanguageInfo } from "../../integrations/biel";

export function createLanguagesRepo(db: Database) {
  return {
    /**
     * Ensure a languages row exists for this ietf code. If already present its
     * id is reused; otherwise a row is created from BIEL language info. Returns
     * the language id.
     */
    async upsert(info: LanguageInfo): Promise<number> {
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

    /** English name of a language (for the AI prompt), or "" if unknown. */
    async getName(languageId: number): Promise<string> {
      const [row] = await db
        .select({ name: languagesTable.angName })
        .from(languagesTable)
        .where(eq(languagesTable.id, languageId));
      return row?.name ?? "";
    },
  };
}

export type LanguagesRepo = ReturnType<typeof createLanguagesRepo>;
