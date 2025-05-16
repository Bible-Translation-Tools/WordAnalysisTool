import { SQL_BATCH_LIMIT } from "./constants";
import { PrismaD1 } from "@prisma/adapter-d1";
import { PrismaClient } from "@prisma/client";
import SqlString from "sqlstring";
import { ModelResult, WordId } from "./types";

export default class DbHelper {
  private prisma: PrismaClient;

  constructor(database: D1Database) {
    const adapter = new PrismaD1(database);
    this.prisma = new PrismaClient({ adapter });
  }

  getPrisma(): PrismaClient {
    return this.prisma;
  }

  async insertOtUpdateWords(words: string[], batchId: string) {
    for (let i = 0; i < words.length; i += SQL_BATCH_LIMIT) {
      const batch = words.slice(i, i + SQL_BATCH_LIMIT);
      const wordValues = batch.map((item) => {
        return `(${SqlString.escape(item)},${SqlString.escape(batchId)})`;
      });

      if (wordValues.length > 0) {
        await this.prisma.$executeRawUnsafe(
          `INSERT OR REPLACE INTO Words (word, batch_id) VALUES ${wordValues.join()}`
        );
      }
    }
  }

  async fetchWordIds(words: string[], batchId: string): Promise<number[]> {
    const wordIds = [];
    for (let i = 0; i < words.length; i += SQL_BATCH_LIMIT) {
      const batch = words.slice(i, i + SQL_BATCH_LIMIT);
      if (batch.length > 0) {
        const escaped = batch.map((w) => SqlString.escape(w));
        const result: WordId[] = await this.prisma.$queryRawUnsafe(
          `SELECT id FROM Words WHERE batch_id = ${SqlString.escape(
            batchId
          )} AND word IN (${escaped.join(",")})`
        );
        wordIds.push(...result.map((w) => w.id));
      }
    }
    return wordIds;
  }

  async insertOrUpdateModels(wordIds: number[], models: string[]) {
    const limit = Math.round(SQL_BATCH_LIMIT / models.length);
    for (let i = 0; i < wordIds.length; i += limit) {
      const wordIdBatch = wordIds.slice(i, i + limit);
      if (wordIdBatch.length > 0) {
        const modelValuesBatch = wordIdBatch.flatMap((wordId) =>
          models.map((model) => {
            return `(${SqlString.escape(model)},-1,${wordId})`;
          })
        );

        if (modelValuesBatch.length > 0) {
          await this.prisma.$executeRawUnsafe(
            `INSERT OR REPLACE INTO Models (model, status, word_id) VALUES ${modelValuesBatch.join()}`
          );
        }
      }
    }
  }

  async updateModelResults(
    words: string[],
    batchId: string,
    results: ModelResult[]
  ): Promise<string | null> {
    let errorDetails = null;

    let sql = `UPDATE Models AS m SET status = CASE w.word `;

    const params = [];
    let caseStatements = "";
    let wordList = [];
    let modelList = [];

    for (const modelResult of results) {
      const modelName = modelResult.model;
      modelList.push(modelName);
      for (const result of modelResult.results) {
        const word = result.word.trim();
        if (words.includes(word)) {
          const escapedWord = SqlString.escape(word);
          const escapedStatus = SqlString.escape(result.status);

          caseStatements += `WHEN ${escapedWord} THEN ${escapedStatus} `;
          wordList.push(word);
        } else {
          errorDetails = `model ${modelName} returned wrong word result: ${word}`;
        }
      }
    }

    sql +=
      caseStatements +
      ` ELSE m.status END FROM Words AS w WHERE m.word_id = w.id AND m.model IN (${modelList
        .map((m) => SqlString.escape(m))
        .join()}) AND w.batch_id = ${SqlString.escape(batchId)}`;

    console.warn(sql);

    await this.prisma.$executeRawUnsafe(sql);

    return errorDetails;
  }

  async getCompletedWordsCount(batchId: string): Promise<number> {
    return await this.prisma.word.count({
      where: {
        batch_id: batchId,
        models: {
          none: {
            status: {
              lte: -1,
            },
          },
        },
      },
    });
  }
}
