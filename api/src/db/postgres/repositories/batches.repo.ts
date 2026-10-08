import { asc, eq } from "drizzle-orm";
import { PgDb } from "../client";
import {
  batchesTable,
  languagesTable,
  modelsTable,
  resourcesTable,
  usersTable,
  wordsTable,
} from "../schema";
import { BatchesRepo } from "../../store";

export function createBatchesRepo(db: PgDb): BatchesRepo {
  return {
    findByResourceId(resourceId) {
      return db.query.batchesTable.findFirst({
        where: eq(batchesTable.resourceId, resourceId),
      });
    },

    findByResourceIdWithUser(resourceId) {
      return db.query.batchesTable.findFirst({
        where: eq(batchesTable.resourceId, resourceId),
        with: { user: true },
      });
    },

    findIngesting() {
      return db.query.batchesTable.findFirst({
        where: eq(batchesTable.ingesting, true),
        orderBy: [asc(batchesTable.createdAt)],
      });
    },

    findPending() {
      return db.query.batchesTable.findFirst({
        where: eq(batchesTable.pending, true),
        orderBy: [asc(batchesTable.createdAt)],
      });
    },

    async create(values) {
      await db.insert(batchesTable).values(values);
    },

    async updateById(id, values) {
      await db.update(batchesTable).set(values).where(eq(batchesTable.id, id));
    },

    async pause(id) {
      const rows = await db
        .update(batchesTable)
        .set({ pending: false, error: null })
        .where(eq(batchesTable.id, id))
        .returning({ id: batchesTable.id });
      return rows.length > 0;
    },

    async deleteById(id) {
      const rows = await db
        .delete(batchesTable)
        .where(eq(batchesTable.id, id))
        .returning({ id: batchesTable.id });
      return rows.length > 0;
    },

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
