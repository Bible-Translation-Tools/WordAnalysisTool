import { and, eq, sql } from "drizzle-orm";
import { SqliteDb } from "../client";
import { languagesTable, resourcesTable } from "../schema";
import { ResourcesRepo } from "../../store";

export function createResourcesRepo(db: SqliteDb): ResourcesRepo {
  return {
    async getRef(resourceId) {
      const [row] = await db
        .select({
          ietf: languagesTable.code,
          resourceType: resourcesTable.resourceType,
          name: languagesTable.angName,
        })
        .from(resourcesTable)
        .innerJoin(
          languagesTable,
          eq(resourcesTable.languageId, languagesTable.id),
        )
        .where(eq(resourcesTable.id, resourceId));
      return row ?? null;
    },

    async getId(ietf, resourceType) {
      const [row] = await db
        .select({ id: resourcesTable.id })
        .from(resourcesTable)
        .innerJoin(
          languagesTable,
          eq(resourcesTable.languageId, languagesTable.id),
        )
        .where(
          and(
            eq(languagesTable.code, ietf),
            eq(resourcesTable.resourceType, resourceType),
          ),
        );
      return row?.id ?? null;
    },

    async upsert(resourceType, languageId) {
      const [row] = await db
        .insert(resourcesTable)
        .values({ resourceType, languageId })
        .onConflictDoUpdate({
          target: [resourcesTable.resourceType, resourcesTable.languageId],
          set: { resourceType: sql`excluded.resource_type` },
        })
        .returning({ id: resourcesTable.id });
      return row.id;
    },
  };
}
