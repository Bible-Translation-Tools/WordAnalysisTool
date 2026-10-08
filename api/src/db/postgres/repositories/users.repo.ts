import { and, eq, gt, sql } from "drizzle-orm";
import { PgDb } from "../client";
import { usersTable } from "../schema";
import { UsersRepo } from "../../store";

export function createUsersRepo(db: PgDb): UsersRepo {
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
            username: sql`EXCLUDED.username`,
            accessToken: sql`EXCLUDED.access_token`,
            refreshToken: sql`EXCLUDED.refresh_token`,
            tokenType: sql`EXCLUDED.token_type`,
            state: sql`EXCLUDED.state`,
            updatedAt: new Date(),
          },
        });
    },
  };
}
