/**
 * SQLite (Cloudflare D1) schema. Mirrors `../postgres/schema.ts` column for
 * column; the column *names* must stay identical so that data can be copied
 * between drivers. Booleans are stored as 0/1 and timestamps as unix seconds.
 */
import { relations, sql } from "drizzle-orm";
import {
  sqliteTable,
  text,
  integer,
  uniqueIndex,
  index,
} from "drizzle-orm/sqlite-core";

const now = () => sql`(unixepoch())`;

export const usersTable = sqliteTable(
  "users",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    wacsUserId: integer("wacs_user_id").notNull(),
    username: text("username").notNull(),
    email: text("email").notNull(),
    accessToken: text("access_token"),
    refreshToken: text("refresh_token"),
    tokenType: text("token_type"),
    state: text("state"),
    createdAt: integer("created_at", { mode: "timestamp" })
      .default(now())
      .notNull(),
    updatedAt: integer("updated_at", { mode: "timestamp" })
      .default(now())
      .notNull(),
  },
  (table) => [uniqueIndex("idx_unique_user").on(table.email)],
);

export const languagesTable = sqliteTable(
  "languages",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    code: text("lc").notNull(),
    name: text("ln").notNull(),
    angName: text("ang").notNull(),
    direction: text("ld").notNull(),
    gateway: integer("gw", { mode: "boolean" }).default(false).notNull(),
  },
  (table) => [uniqueIndex("idx_unique_language").on(table.code)],
);

export const resourcesTable = sqliteTable(
  "resources",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    resourceType: text("resource_type").notNull(),
    languageId: integer("language_id")
      .notNull()
      .references(() => languagesTable.id, { onDelete: "cascade" }),
  },
  (table) => [
    uniqueIndex("idx_unique_resource").on(
      table.languageId,
      table.resourceType,
    ),
  ],
);

export const versesTable = sqliteTable(
  "verses",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    bookCode: text("book_code").notNull(),
    chapter: integer("chapter").notNull(),
    verse: text("verse").notNull(),
    text: text("text").notNull(),
    resourceId: integer("resource_id")
      .notNull()
      .references(() => resourcesTable.id, { onDelete: "cascade" }),
  },
  (table) => [
    uniqueIndex("idx_unique_verse").on(
      table.resourceId,
      table.bookCode,
      table.chapter,
      table.verse,
    ),
  ],
);

export const batchesTable = sqliteTable(
  "batches",
  {
    id: text("id").primaryKey().notNull(),
    languageId: integer("language_id").references(() => languagesTable.id, {
      onDelete: "set null",
    }),
    resourceId: integer("resource_id").references(() => resourcesTable.id, {
      onDelete: "set null",
    }),
    // Reference translation resource used for AI context, chosen per batch.
    refResourceId: integer("ref_resource_id").references(
      () => resourcesTable.id,
      { onDelete: "set null" },
    ),
    userId: integer("user_id")
      .notNull()
      .references(() => usersTable.id, { onDelete: "cascade" }),
    ingesting: integer("ingesting", { mode: "boolean" })
      .default(false)
      .notNull(),
    pending: integer("pending", { mode: "boolean" }).default(false).notNull(),
    apostropheIsSeparator: integer("apostrophe_is_separator", {
      mode: "boolean",
    })
      .default(true)
      .notNull(),
    models: text("models"),
    error: text("error"),
    retries: integer("retries").default(0).notNull(),
    createdAt: integer("created_at", { mode: "timestamp" })
      .default(now())
      .notNull(),
    updatedAt: integer("updated_at", { mode: "timestamp" })
      .default(now())
      .notNull(),
  },
  (table) => [
    uniqueIndex("idx_unique_batch").on(table.resourceId),
    index("idx_batch_user_id").on(table.userId),
    index("idx_batch_language_id").on(table.languageId),
    index("idx_batch_ref_resource_id").on(table.refResourceId),
  ],
);

export const wordsTable = sqliteTable(
  "words",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    word: text("word").notNull(),
    batchId: text("batch_id")
      .notNull()
      .references(() => batchesTable.id, { onDelete: "cascade" }),
    verseId: integer("verse_id")
      .notNull()
      .references(() => versesTable.id, { onDelete: "cascade" }),
    createdAt: integer("created_at", { mode: "timestamp" })
      .default(now())
      .notNull(),
  },
  (table) => [
    uniqueIndex("idx_unique_word").on(table.batchId, table.word),
    index("idx_word_verse_id").on(table.verseId),
  ],
);

export const modelsTable = sqliteTable(
  "models",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    model: text("model").notNull(),
    status: integer("status").notNull(),
    retries: integer("retries").default(0).notNull(),
    wordId: integer("word_id")
      .notNull()
      .references(() => wordsTable.id, { onDelete: "cascade" }),
    createdAt: integer("created_at", { mode: "timestamp" })
      .default(now())
      .notNull(),
  },
  (table) => [
    uniqueIndex("idx_unique_model").on(table.wordId, table.model),
  ],
);

export const wordReviewsTable = sqliteTable(
  "word_reviews",
  {
    pk: integer("pk").primaryKey({ autoIncrement: true }),
    wordId: integer("word_id")
      .notNull()
      .references(() => wordsTable.id, { onDelete: "cascade" }),
    userId: integer("user_id")
      .notNull()
      .references(() => usersTable.id, { onDelete: "cascade" }),
    correct: integer("correct", { mode: "boolean" }).notNull(),
  },
  (table) => [
    uniqueIndex("idx_unique_word_review").on(table.wordId, table.userId),
  ],
);

export const userRelations = relations(usersTable, ({ many }) => ({
  batches: many(batchesTable),
}));

export const languageRelations = relations(languagesTable, ({ many }) => ({
  resources: many(resourcesTable),
}));

export const resourceRelations = relations(
  resourcesTable,
  ({ one, many }) => ({
    language: one(languagesTable, {
      fields: [resourcesTable.languageId],
      references: [languagesTable.id],
    }),
    verses: many(versesTable),
  }),
);

export const verseRelations = relations(versesTable, ({ one, many }) => ({
  resource: one(resourcesTable, {
    fields: [versesTable.resourceId],
    references: [resourcesTable.id],
  }),
  words: many(wordsTable),
}));

export const batchRelations = relations(batchesTable, ({ one, many }) => ({
  user: one(usersTable, {
    fields: [batchesTable.userId],
    references: [usersTable.id],
  }),
  language: one(languagesTable, {
    fields: [batchesTable.languageId],
    references: [languagesTable.id],
  }),
  resource: one(resourcesTable, {
    fields: [batchesTable.resourceId],
    references: [resourcesTable.id],
    relationName: "batchResource",
  }),
  refResource: one(resourcesTable, {
    fields: [batchesTable.refResourceId],
    references: [resourcesTable.id],
    relationName: "batchRefResource",
  }),
  words: many(wordsTable),
}));

export const wordRelations = relations(wordsTable, ({ one, many }) => ({
  batch: one(batchesTable, {
    fields: [wordsTable.batchId],
    references: [batchesTable.id],
  }),
  verse: one(versesTable, {
    fields: [wordsTable.verseId],
    references: [versesTable.id],
  }),
  models: many(modelsTable),
  reviews: many(wordReviewsTable),
}));

export const modelRelations = relations(modelsTable, ({ one }) => ({
  word: one(wordsTable, {
    fields: [modelsTable.wordId],
    references: [wordsTable.id],
  }),
}));

export const wordReviewsRelations = relations(wordReviewsTable, ({ one }) => ({
  word: one(wordsTable, {
    fields: [wordReviewsTable.wordId],
    references: [wordsTable.id],
  }),
  user: one(usersTable, {
    fields: [wordReviewsTable.userId],
    references: [usersTable.id],
  }),
}));
