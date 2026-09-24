import { asc, eq } from "drizzle-orm";
import { Database } from "../client";
import {
  batchesTable,
  languagesTable,
  modelsTable,
  resourcesTable,
  usersTable,
  wordsTable,
} from "../schema";

export type BatchEntity = typeof batchesTable.$inferSelect;

export function createBatchesRepo(db: Database) {
  return {
    findByResourceId(resourceId: number): Promise<BatchEntity | undefined> {
      return db.query.batchesTable.findFirst({
        where: eq(batchesTable.resourceId, resourceId),
      });
    },

    /** Batch keyed by resource, with its creator (for stats / review screens). */
    findByResourceIdWithUser(resourceId: number) {
      return db.query.batchesTable.findFirst({
        where: eq(batchesTable.resourceId, resourceId),
        with: { user: true },
      });
    },

    /** Oldest batch currently being ingested, if any. */
    findIngesting(): Promise<BatchEntity | undefined> {
      return db.query.batchesTable.findFirst({
        where: eq(batchesTable.ingesting, true),
        orderBy: [asc(batchesTable.createdAt)],
      });
    },

    /** Oldest batch pending AI processing, if any. */
    findPending(): Promise<BatchEntity | undefined> {
      return db.query.batchesTable.findFirst({
        where: eq(batchesTable.pending, true),
        orderBy: [asc(batchesTable.createdAt)],
      });
    },

    async create(values: typeof batchesTable.$inferInsert): Promise<void> {
      await db.insert(batchesTable).values(values);
    },

    async updateById(
      id: string,
      values: Partial<typeof batchesTable.$inferInsert>,
    ): Promise<void> {
      await db.update(batchesTable).set(values).where(eq(batchesTable.id, id));
    },

    /** Stop a pending batch. Returns true if a row was affected. */
    async pause(id: string): Promise<boolean> {
      const rows = await db
        .update(batchesTable)
        .set({ pending: false, error: null })
        .where(eq(batchesTable.id, id))
        .returning();
      return rows.length > 0;
    },

    async deleteById(id: string): Promise<boolean> {
      const rows = await db
        .delete(batchesTable)
        .where(eq(batchesTable.id, id))
        .returning();
      return rows.length > 0;
    },

    /** Distinct batches that have at least one processed word, with creator. */
    listRecent() {
      return db
        .selectDistinct({
          id: batchesTable.id,
          ietfCode: languagesTable.code,
          resourceType: resourcesTable.resourceType,
          user: { username: usersTable.username },
        })
        .from(batchesTable)
        .innerJoin(wordsTable, eq(batchesTable.id, wordsTable.batchId))
        .innerJoin(modelsTable, eq(wordsTable.id, modelsTable.wordId))
        .innerJoin(usersTable, eq(batchesTable.userId, usersTable.id))
        .innerJoin(resourcesTable, eq(batchesTable.resourceId, resourcesTable.id))
        .innerJoin(
          languagesTable,
          eq(resourcesTable.languageId, languagesTable.id),
        );
    },
  };
}

export type BatchesRepo = ReturnType<typeof createBatchesRepo>;
