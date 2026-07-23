import { and, eq, gt, sql } from "drizzle-orm";
import { Database } from "../client";
import { usersTable } from "../schema";

export type UserEntity = typeof usersTable.$inferSelect;

export type OAuthUpsert = {
  email: string;
  username: string;
  wacsUserId: number;
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  state: string;
};

export function createUsersRepo(db: Database) {
  return {
    findByEmail(email: string): Promise<UserEntity | undefined> {
      return db.query.usersTable.findFirst({
        where: eq(usersTable.email, email),
      });
    },

    /** A user whose login `state` is set and newer than `since` (unexpired). */
    findByFreshState(
      state: string,
      since: Date,
    ): Promise<UserEntity | undefined> {
      return db.query.usersTable.findFirst({
        where: and(
          eq(usersTable.state, state),
          gt(usersTable.updatedAt, since),
        ),
      });
    },

    async clearState(state: string): Promise<void> {
      await db
        .update(usersTable)
        .set({ state: null, updatedAt: new Date() })
        .where(eq(usersTable.state, state));
    },

    async upsertFromOAuth(values: OAuthUpsert): Promise<void> {
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

export type UsersRepo = ReturnType<typeof createUsersRepo>;
