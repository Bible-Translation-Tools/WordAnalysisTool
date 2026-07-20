import { SQL_BATCH_LIMIT } from "./constants";
import { BatchError, LanguageData, ModelResult, WordData } from "./types";
import * as schema from "./db/schema";
import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import { and, eq, exists, inArray, isNull, lte, not, sql } from "drizzle-orm";

export default class DbHelper {
  private db;

  constructor(env: CloudflareBindings) {
    const client = postgres(env.DATABASE_URL);
    this.db = drizzle(client, { schema });
  }

  getDb() {
    return this.db;
  }

  async upsertLanguages(languages: LanguageData[]): Promise<number> {
    let count = 0;
    for (let i = 0; i < languages.length; i += SQL_BATCH_LIMIT) {
      const batch = languages.slice(i, i + SQL_BATCH_LIMIT);
      const values = batch.map((lang) => ({
        code: lang.lc,
        name: lang.ln,
        angName: lang.ang,
        direction: lang.ld,
        gateway: lang.gw,
      }));

      if (values.length > 0) {
        const result = await this.db
          .insert(schema.languagesTable)
          .values(values)
          .onConflictDoUpdate({
            target: schema.languagesTable.code,
            set: {
              name: sql`excluded.ln`,
              angName: sql`excluded.ang`,
              direction: sql`excluded.ld`,
              gateway: sql`excluded.gw`,
            },
          })
          .returning({ id: schema.languagesTable.id });
        count += result.length;
      }
    }
    return count;
  }

  async insertWords(words: WordData[], batchId: string) {
    for (let i = 0; i < words.length; i += SQL_BATCH_LIMIT) {
      const batch = words.slice(i, i + SQL_BATCH_LIMIT);
      const wordValues = batch.map((word) => ({
        word: word.word,
        ref: word.ref,
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

  async fetchWordIds(words: WordData[], batchId: string): Promise<number[]> {
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
              inArray(
                schema.wordsTable.word,
                batch.map((w) => w.word),
              ),
              not(
                exists(
                  this.db
                    .select({ id: schema.modelsTable.id })
                    .from(schema.modelsTable)
                    .where(eq(schema.modelsTable.wordId, schema.wordsTable.id)),
                ),
              ),
            ),
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
          })),
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
    batchId: string,
    results: ModelResult[],
  ): Promise<BatchError | null> {
    for (const modelResult of results) {
      const statusCases: Array<ReturnType<typeof sql>> = [];
      const updatedWords: string[] = [];

      for (const result of modelResult.results) {
        const word = result.word.trim();

        statusCases.push(
          sql`WHEN ${schema.wordsTable.word} = ${word} THEN ${result.status}`,
        );
        updatedWords.push(word);
      }

      if (statusCases.length > 0) {
        const statusFragment = sql.join(statusCases, sql` `);

        await this.db
          .update(schema.modelsTable)
          .set({
            status: sql`CASE ${statusFragment} ELSE ${schema.modelsTable.status} END`,
            retries: modelResult.retries,
          })
          .from(schema.wordsTable)
          .where(
            and(
              eq(schema.modelsTable.wordId, schema.wordsTable.id),
              eq(schema.modelsTable.model, modelResult.model),
              eq(schema.wordsTable.batchId, batchId),
              inArray(schema.wordsTable.word, updatedWords), // Only touch words returned by this model
            ),
          );
      }
    }

    return null;
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
          lte(schema.modelsTable.status, -1),
        ),
      )
      .where(
        and(
          eq(schema.wordsTable.batchId, batchId),
          isNull(schema.modelsTable.id),
        ),
      );

    return result[0].count;
  }
}
