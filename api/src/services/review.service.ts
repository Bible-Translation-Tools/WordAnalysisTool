import { and, asc, count, eq, inArray, max, min, sql } from "drizzle-orm";
import { unionAll } from "drizzle-orm/pg-core";
import { Database } from "../db/client";
import {
  modelsTable,
  versesTable,
  wordReviewsTable,
  wordsTable,
} from "../db/schema";
import { BatchProgress, WordResponse } from "../types";
import { emptyProgress } from "./stats.service";

// Total pool of "good" (unanimous 0/1) words presented for review.
const REVIEW_POOL_LIMIT = 370;

/**
 * Build the review pool for a batch/user: unanimous correct/incorrect words,
 * sampled proportionally down to REVIEW_POOL_LIMIT, joined to their verse and
 * the user's existing review. Returns the whole ordered pool + review progress;
 * the client walks it one word at a time.
 */
export async function sampleReviewWords(
  db: Database,
  batchId: string,
  userId: number,
): Promise<{ output: WordResponse[]; progress: BatchProgress }> {
  const categorizedGoodWords = db
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
    .as("categorized_good_words");

  const categoryCounts = await db
    .select({
      status: categorizedGoodWords.status,
      count: count().as("count"),
    })
    .from(categorizedGoodWords)
    .groupBy(categorizedGoodWords.status);

  const totalGoodWords = categoryCounts.reduce((sum, row) => sum + row.count, 0);

  let sampledGoodWords;
  if (totalGoodWords <= REVIEW_POOL_LIMIT) {
    sampledGoodWords = db
      .select({ wordId: categorizedGoodWords.wordId })
      .from(categorizedGoodWords)
      .as("good_words");
  } else {
    const limitsPerStatus = categoryCounts.map((category) => ({
      status: category.status,
      limit: Math.round((category.count / totalGoodWords) * REVIEW_POOL_LIMIT),
    }));

    const summedLimits = limitsPerStatus.reduce((sum, c) => sum + c.limit, 0);
    if (summedLimits !== REVIEW_POOL_LIMIT && limitsPerStatus.length > 0) {
      limitsPerStatus[0].limit += REVIEW_POOL_LIMIT - summedLimits;
    }

    const queriesPerStatus = limitsPerStatus.map((c) =>
      db
        .select({ wordId: categorizedGoodWords.wordId })
        .from(categorizedGoodWords)
        .where(eq(categorizedGoodWords.status, c.status))
        .limit(c.limit),
    );

    if (queriesPerStatus.length === 0) {
      sampledGoodWords = db
        .select({ wordId: modelsTable.wordId })
        .from(modelsTable)
        .where(sql`false`)
        .as("good_words");
    } else if (queriesPerStatus.length === 1) {
      sampledGoodWords = queriesPerStatus[0].as("good_words");
    } else {
      const [first, second, ...rest] = queriesPerStatus;
      sampledGoodWords = unionAll(first, second, ...rest).as("good_words");
    }
  }

  const wordsData = await db
    .select({
      word: wordsTable.word,
      review: wordReviewsTable,
      book: versesTable.bookCode,
      chapter: versesTable.chapter,
      verse: versesTable.verse,
      text: versesTable.text,
    })
    .from(wordsTable)
    .innerJoin(sampledGoodWords, eq(wordsTable.id, sampledGoodWords.wordId))
    .innerJoin(versesTable, eq(wordsTable.verseId, versesTable.id))
    .leftJoin(
      wordReviewsTable,
      and(
        eq(wordsTable.id, wordReviewsTable.wordId),
        eq(wordReviewsTable.userId, userId),
      ),
    )
    .orderBy(asc(wordsTable.word));

  const output: WordResponse[] = wordsData.map((row) => ({
    word: row.word,
    ref: `${row.book}:${row.chapter}:${row.verse}`,
    text: row.text,
    correct: row.review ? row.review.correct : null,
    results: [],
  }));

  const progress: BatchProgress = {
    ...emptyProgress,
    reviewed: output.filter((word) => word.correct !== null).length,
    total: output.length,
  };

  return { output, progress };
}
