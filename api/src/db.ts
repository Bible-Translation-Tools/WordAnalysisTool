import { SQL_BATCH_LIMIT } from "./constants";
import { ModelResult } from "./types";
import * as schema from "./db/schema";
import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import { and, eq, inArray, isNull, lte, sql } from "drizzle-orm";

export default class DbHelper {
  private db;

  constructor(env: CloudflareBindings) {
    const client = postgres(env.DATABASE_URL);
    this.db = drizzle(client, { schema });
  }

  getDb() {
    return this.db;
  }

  async insertWords(words: string[], batchId: string) {
    for (let i = 0; i < words.length; i += SQL_BATCH_LIMIT) {
      const batch = words.slice(i, i + SQL_BATCH_LIMIT);
      const wordValues = batch.map((word) => ({
        word: word,
        batchId: batchId,
      }));

      if (wordValues.length > 0) {
        await this.db
          .insert(schema.wordsTable)
          .values(wordValues)
          .onConflictDoNothing({
            target: [schema.wordsTable.word, schema.wordsTable.batchId],
          });
      }
    }
  }

  async fetchWordIds(words: string[], batchId: string): Promise<number[]> {
    const wordIds = [];
    for (let i = 0; i < words.length; i += SQL_BATCH_LIMIT) {
      const batch = words.slice(i, i + SQL_BATCH_LIMIT);
      if (batch.length > 0) {
        const result = await this.db
          .select({
            id: schema.wordsTable.id,
          })
          .from(schema.wordsTable)
          .where(
            and(
              eq(schema.wordsTable.batchId, batchId),
              inArray(schema.wordsTable.word, batch)
            )
          );

        wordIds.push(...result.map((row) => row.id));
      }
    }
    return wordIds;
  }

  async insertModels(wordIds: number[], models: string[]) {
    const limit = Math.round(SQL_BATCH_LIMIT / models.length);
    for (let i = 0; i < wordIds.length; i += limit) {
      const wordIdBatch = wordIds.slice(i, i + limit);
      if (wordIdBatch.length > 0) {
        const modelValuesBatch = wordIdBatch.flatMap((wordId) =>
          models.map((model) => ({
            model: model,
            status: -1,
            wordId: wordId,
          }))
        );
        if (modelValuesBatch.length > 0) {
          await this.db
            .insert(schema.modelsTable)
            .values(modelValuesBatch)
            .onConflictDoNothing({
              target: [schema.modelsTable.model, schema.modelsTable.wordId],
            });
        }
      }
    }
  }

  async updateModelResults(
    words: string[],
    batchId: string,
    results: ModelResult[]
  ): Promise<string | null> {
    let error = null;
    const wordsSet = new Set(words);
    const modelNames: string[] = [];
    const wordStatusMap = new Map<string, number>();

    for (const modelResult of results) {
      const modelName = modelResult.model;
      if (!modelNames.includes(modelName)) {
        modelNames.push(modelName);
      }

      for (const result of modelResult.results) {
        const word = result.word.trim();
        if (wordsSet.has(word)) {
          wordStatusMap.set(word, result.status);
        } else {
          error = `Model "${modelName}" returned a result for word "${word}" which is not in the allowed word list.`;
        }
      }
    }

    if (modelNames.length === 0 || wordStatusMap.size === 0) {
      return error;
    }

    const caseWhenParts: Array<ReturnType<typeof sql>> = [];
    for (const [word, status] of wordStatusMap.entries()) {
      caseWhenParts.push(sql`WHEN ${word} THEN ${status}`);
    }

    const caseWhenFragment = sql.join(caseWhenParts, sql` `);

    await this.db
      .update(schema.modelsTable)
      .set({
        status: sql`CASE ${schema.wordsTable.word} ${caseWhenFragment} ELSE ${schema.modelsTable.status} END`,
      })
      .from(schema.wordsTable)
      .where(
        and(
          eq(schema.modelsTable.wordId, schema.wordsTable.id),
          inArray(schema.modelsTable.model, modelNames),
          eq(schema.wordsTable.batchId, batchId)
        )
      );

    return error;
  }

  async getCompletedWordsCount(batchId: string): Promise<number> {
    const result = await this.db
      .select({
        count: sql<number>`count(${schema.wordsTable.id})`,
      })
      .from(schema.wordsTable)
      .leftJoin(
        schema.modelsTable,
        and(
          eq(schema.wordsTable.id, schema.modelsTable.wordId),
          lte(schema.modelsTable.status, -1)
        )
      )
      .where(
        and(
          eq(schema.wordsTable.batchId, batchId),
          isNull(schema.modelsTable.id)
        )
      );

    return result[0].count;
  }
}
