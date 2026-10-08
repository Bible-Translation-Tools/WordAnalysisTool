import { and, asc, count, eq, inArray, sql } from "drizzle-orm";
import { unionAll } from "drizzle-orm/pg-core";
import { PgDb } from "../client";
import { versesTable, wordReviewsTable, wordsTable } from "../schema";
import { Consensus, ReviewsRepo } from "../../store";
import { samplingOrder } from "../sql";

/** The review pool is unanimous words; their status is 1 (correct) or 0. */
const GOOD: Record<number, Consensus> = { 1: "correct", 0: "incorrect" };
const STATUS_OF: Partial<Record<Consensus, number>> = { correct: 1, incorrect: 0 };

export function createReviewsRepo(db: PgDb): ReviewsRepo {
  const goodWords = (batchId: string) =>
    and(
      eq(wordsTable.batchId, batchId),
      eq(wordsTable.unanimous, true),
      inArray(wordsTable.consensus, ["correct", "incorrect"]),
    );

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
      const rows = await db
        .select({ consensus: wordsTable.consensus, count: count() })
        .from(wordsTable)
        .where(goodWords(batchId))
        .groupBy(wordsTable.consensus);
      return rows.map((r) => ({ status: STATUS_OF[r.consensus!]!, count: r.count }));
    },

    async fetchPool(batchId, userId, limits) {
      let pool;
      if (limits === null) {
        pool = db
          .select({ wordId: wordsTable.id })
          .from(wordsTable)
          .where(goodWords(batchId))
          .as("pool");
      } else if (limits.length === 0) {
        return [];
      } else {
        const perStatus = limits.map((l) =>
          db
            .select({ wordId: wordsTable.id })
            .from(wordsTable)
            .where(and(goodWords(batchId), eq(wordsTable.consensus, GOOD[l.status])))
            .orderBy(samplingOrder(wordsTable.id, userId))
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
