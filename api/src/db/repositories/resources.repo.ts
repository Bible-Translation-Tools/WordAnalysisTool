import { and, eq, sql } from "drizzle-orm";
import { Database } from "../client";
import { languagesTable, resourcesTable } from "../schema";

export type ResourceRef = {
  ietf: string;
  resourceType: string;
  name: string;
};

export function createResourcesRepo(db: Database) {
  return {
    /** Resolve a resource id to its ietf code, resource type, and language name. */
    async getRef(resourceId: number): Promise<ResourceRef | null> {
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

    /**
     * Resolve (ietf code, resource type) to an existing resource id, or null.
     * Used to locate a batch (which is keyed by its resource) from URL params.
     */
    async getId(ietf: string, resourceType: string): Promise<number | null> {
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

    /** Ensure a resources row exists for (resourceType, languageId). Returns its id. */
    async upsert(resourceType: string, languageId: number): Promise<number> {
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

export type ResourcesRepo = ReturnType<typeof createResourcesRepo>;
