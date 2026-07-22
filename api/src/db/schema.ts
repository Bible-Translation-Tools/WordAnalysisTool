import { relations } from "drizzle-orm";
import {
  pgTable,
  text,
  integer,
  boolean,
  timestamp,
  uniqueIndex,
  index,
  varchar,
} from "drizzle-orm/pg-core";

export const usersTable = pgTable(
  "users",
  {
    id: integer("id").primaryKey().generatedAlwaysAsIdentity(),
    wacsUserId: integer("wacs_user_id").notNull(),
    username: varchar("username", { length: 255 }).notNull(),
    email: varchar("email", { length: 255 }).notNull(),
    accessToken: text("access_token"),
    refreshToken: text("refresh_token"),
    tokenType: varchar("token_type", { length: 255 }),
    state: varchar("state", { length: 255 }),
    createdAt: timestamp("created_at").defaultNow().notNull(),
    updatedAt: timestamp("updated_at").defaultNow().notNull(),
  },
  (table) => [uniqueIndex("idx_unique_user").on(table.email)],
);

export const languagesTable = pgTable(
  "languages",
  {
    id: integer("id").primaryKey().generatedAlwaysAsIdentity(),
    code: varchar("lc", { length: 255 }).notNull(),
    name: text("ln").notNull(),
    angName: text("ang").notNull(),
    direction: text("ld").notNull(),
    gateway: boolean("gw").default(false).notNull(),
  },
  (table) => [uniqueIndex("idx_unique_language").on(table.code)],
);

export const resourcesTable = pgTable(
  "resources",
  {
    id: integer("id").primaryKey().generatedAlwaysAsIdentity(),
    resourceType: text("resource_type").notNull(),
    languageId: integer("language_id")
      .notNull()
      .references(() => languagesTable.id, { onDelete: "cascade" }),
  },
  (table) => [
    uniqueIndex("idx_unique_resource").on(
      table.resourceType,
      table.languageId,
    ),
    index("idx_resource_language_id").on(table.languageId),
  ],
);

export const versesTable = pgTable(
  "verses",
  {
    id: integer("id").primaryKey().generatedAlwaysAsIdentity(),
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
      table.bookCode,
      table.chapter,
      table.verse,
      table.resourceId,
    ),
    index("idx_verse_resource_id").on(table.resourceId),
  ],
);

export const batchesTable = pgTable(
  "batches",
  {
    id: varchar("id", { length: 255 }).primaryKey().notNull(),
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
    ingesting: boolean("ingesting").default(false).notNull(),
    pending: boolean("pending").default(false).notNull(),
    apostropheIsSeparator: boolean("apostrophe_is_separator")
      .default(true)
      .notNull(),
    models: text("models"),
    error: text("error"),
    retries: integer("retries").default(0).notNull(),
    createdAt: timestamp("created_at").defaultNow().notNull(),
    updatedAt: timestamp("updated_at").defaultNow().notNull(),
  },
  (table) => [
    uniqueIndex("idx_unique_batch").on(table.resourceId),
    index("idx_batch_user_id").on(table.userId),
    index("idx_batch_language_id").on(table.languageId),
    index("idx_batch_resource_id").on(table.resourceId),
    index("idx_batch_ref_resource_id").on(table.refResourceId),
  ],
);

export const wordsTable = pgTable(
  "words",
  {
    id: integer("id").primaryKey().generatedAlwaysAsIdentity(),
    word: varchar("word", { length: 255 }).notNull(),
    batchId: varchar("batch_id", { length: 255 })
      .notNull()
      .references(() => batchesTable.id, { onDelete: "cascade" }),
    verseId: integer("verse_id")
      .notNull()
      .references(() => versesTable.id, { onDelete: "cascade" }),
    createdAt: timestamp("created_at").defaultNow().notNull(),
  },
  (table) => [
    uniqueIndex("idx_unique_word").on(table.word, table.batchId),
    index("idx_word_batch_id").on(table.batchId),
    index("idx_word_verse_id").on(table.verseId),
  ],
);

export const modelsTable = pgTable(
  "models",
  {
    id: integer("id").primaryKey().generatedAlwaysAsIdentity(),
    model: varchar("model", { length: 255 }).notNull(),
    status: integer("status").notNull(),
    retries: integer("retries").default(0).notNull(),
    wordId: integer("word_id")
      .notNull()
      .references(() => wordsTable.id, { onDelete: "cascade" }),
    createdAt: timestamp("created_at").defaultNow().notNull(),
  },
  (table) => [
    uniqueIndex("idx_unique_model").on(table.model, table.wordId),
    index("idx_model_word_id").on(table.wordId),
  ],
);

export const wordReviewsTable = pgTable(
  "word_reviews",
  {
    pk: integer("pk").primaryKey().generatedAlwaysAsIdentity(),
    wordId: integer("word_id")
      .notNull()
      .references(() => wordsTable.id, { onDelete: "cascade" }),
    userId: integer("user_id")
      .notNull()
      .references(() => usersTable.id, { onDelete: "cascade" }),
    correct: boolean("correct").notNull(),
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
