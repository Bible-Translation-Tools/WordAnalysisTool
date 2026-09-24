import { Database } from "../client";
import { createLanguagesRepo } from "./languages.repo";
import { createResourcesRepo } from "./resources.repo";
import { createVersesRepo } from "./verses.repo";
import { createWordsRepo } from "./words.repo";
import { createModelsRepo } from "./models.repo";
import { createBatchesRepo } from "./batches.repo";
import { createReviewsRepo } from "./reviews.repo";
import { createUsersRepo } from "./users.repo";

/** Build the full set of table repositories over a single Drizzle client. */
export function createRepositories(db: Database) {
  return {
    languages: createLanguagesRepo(db),
    resources: createResourcesRepo(db),
    verses: createVersesRepo(db),
    words: createWordsRepo(db),
    models: createModelsRepo(db),
    batches: createBatchesRepo(db),
    reviews: createReviewsRepo(db),
    users: createUsersRepo(db),
  };
}

export type Repositories = ReturnType<typeof createRepositories>;
