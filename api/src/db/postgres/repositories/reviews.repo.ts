import { and, asc, count, eq, inArray, max, min, sql } from "drizzle-orm";
import { unionAll } from "drizzle-orm/pg-core";
import { PgDb } from "../client";
import {
  modelsTable,
  versesTable,
  wordReviewsTable,
  wordsTable,
} from "../schema";
import { ReviewsRepo } from "../../store";
import { samplingOrder } from "../sql";

export function createReviewsRepo(db: PgDb): ReviewsRepo {
  /** Words of a batch whose models all agree on 0 or 1, with that status. */
  const goodWords = (batchId: string) =>
    db
      .select({
        wordId: modelsTable.wordId,
        status: min(modelsTable.status).as("status"),
      })
      .from(modelsTable)
      .innerJoin(wordsTable, eq(modelsTable.wordId, wordsTable.id))
      .where(eq(wordsTable.batchId, batchId))
      .groupBy(modelsTable.wordId)
      .having(
        and(
          eq(min(modelsTable.status), max(modelsTable.status)),
          inArray(min(modelsTable.status), [0, 1]),
        ),
      )
      .as("good_words");

  return {
    async upsert(review) {
      await db
        .insert(wordReviewsTable)
        .values(review)
        .onConflictDoUpdate({
          target: [wordReviewsTable.wordId, wordReviewsTable.userId],
          set: { correct: sql`excluded.correct` },
        });
    },

    async deleteByBatch(batchId) {
      const deleted = await db
        .delete(wordReviewsTable)
        .where(
          inArray(
            wordReviewsTable.wordId,
            db
              .select({ id: wordsTable.id })
              .from(wordsTable)
              .where(eq(wordsTable.batchId, batchId)),
          ),
        )
        .returning({ pk: wordReviewsTable.pk });
      return deleted.length > 0;
    },

    async countGoodWordsByStatus(batchId) {
      const good = goodWords(batchId);
      const rows = await db
        .select({ status: good.status, count: count() })
        .from(good)
        .groupBy(good.status);
      return rows.map((r) => ({ status: Number(r.status), count: r.count }));
    },

    async fetchPool(batchId, userId, limits) {
      const good = goodWords(batchId);

      let pool;
      if (limits === null) {
        pool = db.select({ wordId: good.wordId }).from(good).as("pool");
      } else if (limits.length === 0) {
        return [];
      } else {
        const perStatus = limits.map((l) =>
          db
            .select({ wordId: good.wordId })
            .from(good)
            .where(eq(good.status, l.status))
            .orderBy(samplingOrder(good.wordId, userId))
            .limit(l.limit),
        );
        if (perStatus.length === 1) {
          pool = perStatus[0].as("pool");
        } else {
          const [first, second, ...rest] = perStatus;
          pool = unionAll(first, second, ...rest).as("pool");
        }
      }

      return db
        .select({
          word: wordsTable.word,
          review: wordReviewsTable,
          book: versesTable.bookCode,
          chapter: versesTable.chapter,
          verse: versesTable.verse,
          text: versesTable.text,
        })
        .from(wordsTable)
        .innerJoin(pool, eq(wordsTable.id, pool.wordId))
        .innerJoin(versesTable, eq(wordsTable.verseId, versesTable.id))
        .leftJoin(
          wordReviewsTable,
          and(
            eq(wordsTable.id, wordReviewsTable.wordId),
            eq(wordReviewsTable.userId, userId),
          ),
        )
        .orderBy(asc(wordsTable.word));
    },
  };
}
