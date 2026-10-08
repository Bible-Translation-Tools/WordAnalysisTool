import { and, eq, gt, sql } from "drizzle-orm";
import { SqliteDb } from "../client";
import { usersTable } from "../schema";
import { UsersRepo } from "../../store";

export function createUsersRepo(db: SqliteDb): UsersRepo {
  return {
    findByEmail(email) {
      return db.query.usersTable.findFirst({
        where: eq(usersTable.email, email),
      });
    },

    findByFreshState(state, since) {
      return db.query.usersTable.findFirst({
        where: and(
          eq(usersTable.state, state),
          gt(usersTable.updatedAt, since),
        ),
      });
    },

    async clearState(state) {
      await db
        .update(usersTable)
        .set({ state: null, updatedAt: new Date() })
        .where(eq(usersTable.state, state));
    },

    async upsertFromOAuth(values) {
      await db
        .insert(usersTable)
        .values(values)
        .onConflictDoUpdate({
          target: usersTable.email,
          set: {
            username: sql`excluded.username`,
            accessToken: sql`excluded.access_token`,
            refreshToken: sql`excluded.refresh_token`,
            tokenType: sql`excluded.token_type`,
            state: sql`excluded.state`,
            updatedAt: new Date(),
          },
        });
    },
  };
}
