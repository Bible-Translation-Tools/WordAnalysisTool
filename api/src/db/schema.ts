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
  (table) => [uniqueIndex("idx_unique_user").on(table.email)]
);

export const batchesTable = pgTable(
  "batches",
  {
    id: varchar("id", { length: 255 }).primaryKey().notNull(),
    ietfCode: varchar("ietf_code", { length: 255 }).notNull(),
    language: varchar("language", { length: 255 }).default("").notNull(),
    resourceType: varchar("resource_type", { length: 255 }).notNull(),
    pending: boolean("pending").default(false).notNull(),
    error: text("error"),
    userId: integer("user_id")
      .notNull()
      .references(() => usersTable.id, { onDelete: "cascade" }),
    createdAt: timestamp("created_at").defaultNow().notNull(),
    updatedAt: timestamp("updated_at").defaultNow().notNull(),
  },
  (table) => [
    uniqueIndex("idx_unique_batch").on(table.ietfCode, table.resourceType),
    index("idx_batch_user_id").on(table.userId),
  ]
);

export const wordsTable = pgTable(
  "words",
  {
    id: integer("id").primaryKey().generatedAlwaysAsIdentity(),
    word: varchar("word", { length: 255 }).notNull(),
    batchId: varchar("batch_id", { length: 255 })
      .notNull()
      .references(() => batchesTable.id, { onDelete: "cascade" }),
    correct: boolean("correct"),
    createdAt: timestamp("created_at").defaultNow().notNull(),
  },
  (table) => [
    uniqueIndex("idx_unique_word").on(table.word, table.batchId),
    index("idx_word_batch_id").on(table.batchId),
    index("idx_word_correct").on(table.correct),
  ]
);

export const modelsTable = pgTable(
  "models",
  {
    id: integer("id").primaryKey().generatedAlwaysAsIdentity(),
    model: varchar("model", { length: 255 }).notNull(),
    status: integer("status").notNull(),
    wordId: integer("word_id")
      .notNull()
      .references(() => wordsTable.id, { onDelete: "cascade" }),
    createdAt: timestamp("created_at").defaultNow().notNull(),
  },
  (table) => [
    uniqueIndex("idx_unique_model").on(table.model, table.wordId),
    index("idx_model_word_id").on(table.wordId),
  ]
);

export const userRelations = relations(usersTable, ({ many }) => ({
  batches: many(batchesTable),
}));

export const batchRelations = relations(batchesTable, ({ one, many }) => ({
  user: one(usersTable, {
    fields: [batchesTable.userId],
    references: [usersTable.id],
  }),
  words: many(wordsTable),
}));

export const wordRelations = relations(wordsTable, ({ one, many }) => ({
  batch: one(batchesTable, {
    fields: [wordsTable.batchId],
    references: [batchesTable.id],
  }),
  models: many(modelsTable),
}));

export const modelRelations = relations(modelsTable, ({ one }) => ({
  word: one(wordsTable, {
    fields: [modelsTable.wordId],
    references: [wordsTable.id],
  }),
}));
