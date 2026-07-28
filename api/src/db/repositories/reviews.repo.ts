import { eq, inArray, sql } from "drizzle-orm";
import { Database } from "../client";
import { wordReviewsTable, wordsTable } from "../schema";

export function createReviewsRepo(db: Database) {
  return {
    async upsert(
      review: { wordId: number; userId: number; correct: boolean },
    ): Promise<void> {
      await db
        .insert(wordReviewsTable)
        .values(review)
        .onConflictDoUpdate({
          target: [wordReviewsTable.wordId, wordReviewsTable.userId],
          set: { correct: sql`excluded.correct` },
        });
    },

    /** Delete every review for words in a batch. Returns true if any were removed. */
    async deleteByBatch(batchId: string): Promise<boolean> {
      const deleted = await db.delete(wordReviewsTable).where(
        inArray(
          wordReviewsTable.wordId,
          db
            .select({ id: wordsTable.id })
            .from(wordsTable)
            .where(eq(wordsTable.batchId, batchId)),
        ),
      );
      return deleted.length > 0;
    },
  };
}

export type ReviewsRepo = ReturnType<typeof createReviewsRepo>;
