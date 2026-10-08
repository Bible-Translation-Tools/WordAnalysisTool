import { PgDb } from "../client";
import {
  batchesTable,
  modelsTable,
  usersTable,
  versesTable,
  wordReviewsTable,
  wordsTable,
} from "../schema";
import {
  BatchEntity,
  ModelEntity,
  Repositories,
  ReviewEntity,
  UserEntity,
  VerseEntity,
  WordEntity,
} from "../../store";
import { createLanguagesRepo } from "./languages.repo";
import { createResourcesRepo } from "./resources.repo";
import { createVersesRepo } from "./verses.repo";
import { createWordsRepo } from "./words.repo";
import { createModelsRepo } from "./models.repo";
import { createBatchesRepo } from "./batches.repo";
import { createReviewsRepo } from "./reviews.repo";
import { createUsersRepo } from "./users.repo";
import { createStatsRepo } from "./stats.repo";

// Compile-time check that this driver's rows match the shared entity types.
type Assert<T extends U, U> = T;
type _EntityChecks = [
  Assert<typeof usersTable.$inferSelect, UserEntity>,
  Assert<typeof batchesTable.$inferSelect, BatchEntity>,
  Assert<typeof versesTable.$inferSelect, VerseEntity>,
  Assert<typeof wordsTable.$inferSelect, WordEntity>,
  Assert<typeof modelsTable.$inferSelect, ModelEntity>,
  Assert<typeof wordReviewsTable.$inferSelect, ReviewEntity>,
];

/** Build the full set of repositories over a single Drizzle Postgres client. */
export function createPostgresRepositories(db: PgDb): Repositories {
  return {
    languages: createLanguagesRepo(db),
    resources: createResourcesRepo(db),
    verses: createVersesRepo(db),
    words: createWordsRepo(db),
    models: createModelsRepo(db),
    batches: createBatchesRepo(db),
    reviews: createReviewsRepo(db),
    users: createUsersRepo(db),
    stats: createStatsRepo(db),
  };
}
