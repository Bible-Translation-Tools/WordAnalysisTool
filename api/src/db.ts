import { SQL_BATCH_LIMIT } from "./constants";
import { BatchError, ModelResult } from "./types";
import * as schema from "./db/schema";
import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import { and, eq, exists, inArray, isNull, lte, not, or, sql } from "drizzle-orm";
import { LanguageInfo } from "./biel";
import { Verse } from "./usfm";

export default class DbHelper {
  private db;

  constructor(env: CloudflareBindings) {
    const client = postgres(env.DATABASE_URL);
    this.db = drizzle(client, { schema });
  }

  getDb() {
    return this.db;
  }

  /**
   * Ensure a languages row exists for this ietf code. If already present
   * (e.g. imported from langnames.json) its id is reused; otherwise a row is
   * created from BIEL language info. Returns the language id.
   */
  async upsertLanguage(info: LanguageInfo): Promise<number> {
    const [row] = await this.db
      .insert(schema.languagesTable)
      .values({
        code: info.ietfCode,
        name: info.nationalName,
        angName: info.englishName,
        direction: info.direction,
        gateway: false,
      })
      .onConflictDoUpdate({
        target: schema.languagesTable.code,
        set: {
          name: sql`excluded.ln`,
          angName: sql`excluded.ang`,
          direction: sql`excluded.ld`,
        },
      })
      .returning({ id: schema.languagesTable.id });
    return row.id;
  }

  /** Resolve a resource id to its ietf code, resource type, and language name. */
  async getResourceRef(
    resourceId: number,
  ): Promise<{ ietf: string; resourceType: string; name: string } | null> {
    const [row] = await this.db
      .select({
        ietf: schema.languagesTable.code,
        resourceType: schema.resourcesTable.resourceType,
        name: schema.languagesTable.angName,
      })
      .from(schema.resourcesTable)
      .innerJoin(
        schema.languagesTable,
        eq(schema.resourcesTable.languageId, schema.languagesTable.id),
      )
      .where(eq(schema.resourcesTable.id, resourceId));
    return row ?? null;
  }

  /**
   * Resolve (ietf code, resource type) to an existing resource id, or null.
   * Used to locate a batch (which is keyed by its resource) from URL params.
   */
  async getResourceId(
    ietf: string,
    resourceType: string,
  ): Promise<number | null> {
    const [row] = await this.db
      .select({ id: schema.resourcesTable.id })
      .from(schema.resourcesTable)
      .innerJoin(
        schema.languagesTable,
        eq(schema.resourcesTable.languageId, schema.languagesTable.id),
      )
      .where(
        and(
          eq(schema.languagesTable.code, ietf),
          eq(schema.resourcesTable.resourceType, resourceType),
        ),
      );
    return row?.id ?? null;
  }

  /** English name of a language (for the AI prompt), or "" if unknown. */
  async getLanguageName(languageId: number): Promise<string> {
    const [row] = await this.db
      .select({ name: schema.languagesTable.angName })
      .from(schema.languagesTable)
      .where(eq(schema.languagesTable.id, languageId));
    return row?.name ?? "";
  }

  /** Ensure a resources row exists for (resourceType, languageId). Returns its id. */
  async upsertResource(
    resourceType: string,
    languageId: number,
  ): Promise<number> {
    const [row] = await this.db
      .insert(schema.resourcesTable)
      .values({ resourceType, languageId })
      .onConflictDoUpdate({
        target: [
          schema.resourcesTable.resourceType,
          schema.resourcesTable.languageId,
        ],
        set: { resourceType: sql`excluded.resource_type` },
      })
      .returning({ id: schema.resourcesTable.id });
    return row.id;
  }

  async insertVerses(verses: Verse[], resourceId: number) {
    // De-duplicate by (book, chapter, verse): a single INSERT ... ON CONFLICT
    // DO UPDATE cannot affect the same conflict target twice, and some source
    // USFM repeats a verse ref. Last occurrence wins. Duplicates are logged so
    // they can be found later in the Cloudflare console.
    const unique = new Map<string, Verse>();
    const duplicates: string[] = [];
    for (const v of verses) {
      const ref = `${v.book}:${v.chapter}:${v.verse}`;
      if (unique.has(ref)) duplicates.push(ref);
      unique.set(ref, v);
    }
    if (duplicates.length > 0) {
      console.error(
        `duplicate verse refs in resource ${resourceId} (${duplicates.length}): ${duplicates.join(", ")}`,
      );
    }
    const deduped = [...unique.values()];

    for (let i = 0; i < deduped.length; i += SQL_BATCH_LIMIT) {
      const batch = deduped.slice(i, i + SQL_BATCH_LIMIT);
      const values = batch.map((v) => ({
        bookCode: v.book,
        chapter: v.chapter,
        verse: v.verse,
        text: v.text,
        resourceId,
      }));

      if (values.length > 0) {
        await this.db
          .insert(schema.versesTable)
          .values(values)
          .onConflictDoUpdate({
            target: [
              schema.versesTable.bookCode,
              schema.versesTable.chapter,
              schema.versesTable.verse,
              schema.versesTable.resourceId,
            ],
            set: { text: sql`excluded.text` },
          });
      }
    }
  }

  async getVersesByResource(resourceId: number): Promise<Verse[]> {
    const rows = await this.db
      .select({
        book: schema.versesTable.bookCode,
        chapter: schema.versesTable.chapter,
        verse: schema.versesTable.verse,
        text: schema.versesTable.text,
      })
      .from(schema.versesTable)
      .where(eq(schema.versesTable.resourceId, resourceId));
    return rows;
  }

  /** Distinct book codes already stored for a resource. */
  async getStoredBookCodes(resourceId: number): Promise<string[]> {
    const rows = await this.db
      .selectDistinct({ book: schema.versesTable.bookCode })
      .from(schema.versesTable)
      .where(eq(schema.versesTable.resourceId, resourceId));
    return rows.map((r) => r.book);
  }

  /**
   * Map "book:chapter:verse" -> verse text for a resource, limited to the given
   * refs. Fetches only what the caller needs (avoids loading a whole Bible).
   */
  async getVerseTextsByRefs(
    resourceId: number,
    refs: { book: string; chapter: number; verse: string }[],
  ): Promise<Map<string, string>> {
    const map = new Map<string, string>();
    if (refs.length === 0) return map;

    // De-duplicate refs, then fetch in chunks to keep the WHERE clause sane.
    const uniqueRefs = [
      ...new Map(
        refs.map((r) => [`${r.book}:${r.chapter}:${r.verse}`, r]),
      ).values(),
    ];

    const CHUNK = 200;
    for (let i = 0; i < uniqueRefs.length; i += CHUNK) {
      const slice = uniqueRefs.slice(i, i + CHUNK);
      const rows = await this.db
        .select({
          book: schema.versesTable.bookCode,
          chapter: schema.versesTable.chapter,
          verse: schema.versesTable.verse,
          text: schema.versesTable.text,
        })
        .from(schema.versesTable)
        .where(
          and(
            eq(schema.versesTable.resourceId, resourceId),
            or(
              ...slice.map((r) =>
                and(
                  eq(schema.versesTable.bookCode, r.book),
                  eq(schema.versesTable.chapter, r.chapter),
                  eq(schema.versesTable.verse, r.verse),
                ),
              ),
            ),
          ),
        );

      for (const r of rows) {
        map.set(`${r.book}:${r.chapter}:${r.verse}`, r.text);
      }
    }
    return map;
  }

  /** Map "book:chapter:verse" -> verse id for a resource. */
  async getVerseRefMap(resourceId: number): Promise<Map<string, number>> {
    const rows = await this.db
      .select({
        id: schema.versesTable.id,
        book: schema.versesTable.bookCode,
        chapter: schema.versesTable.chapter,
        verse: schema.versesTable.verse,
      })
      .from(schema.versesTable)
      .where(eq(schema.versesTable.resourceId, resourceId));

    const map = new Map<string, number>();
    for (const r of rows) {
      map.set(`${r.book}:${r.chapter}:${r.verse}`, r.id);
    }
    return map;
  }

  async insertWords(
    words: { word: string; verseId: number }[],
    batchId: string,
  ) {
    for (let i = 0; i < words.length; i += SQL_BATCH_LIMIT) {
      const batch = words.slice(i, i + SQL_BATCH_LIMIT);
      const wordValues = batch.map((word) => ({
        word: word.word,
        verseId: word.verseId,
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

  async fetchWordIds(
    words: { word: string }[],
    batchId: string,
  ): Promise<number[]> {
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
