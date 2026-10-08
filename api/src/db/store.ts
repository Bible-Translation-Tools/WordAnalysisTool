/**
 * The database contract. Everything outside `src/db` depends only on the types
 * in this file; each driver under `src/db/<driver>` implements `Repositories`
 * with its own schema and SQL dialect. Switch drivers in `src/db/index.ts`.
 */
import type { LanguageInfo } from "../integrations/biel";
import type { Verse } from "../usfm";
import type { ModelResult } from "../types";

// ---------------------------------------------------------------------------
// Row types (what a driver returns; column names are camelCase everywhere)
// ---------------------------------------------------------------------------

export type UserEntity = {
  id: number;
  wacsUserId: number;
  username: string;
  email: string;
  accessToken: string | null;
  refreshToken: string | null;
  tokenType: string | null;
  state: string | null;
  createdAt: Date;
  updatedAt: Date;
};

export type BatchEntity = {
  id: string;
  languageId: number | null;
  resourceId: number | null;
  refResourceId: number | null;
  userId: number;
  ingesting: boolean;
  pending: boolean;
  apostropheIsSeparator: boolean;
  models: string | null;
  error: string | null;
  retries: number;
  createdAt: Date;
  updatedAt: Date;
};

export type BatchInsert = {
  id: string;
  userId: number;
  languageId?: number | null;
  resourceId?: number | null;
  refResourceId?: number | null;
  ingesting?: boolean;
  pending?: boolean;
  apostropheIsSeparator?: boolean;
  models?: string | null;
  error?: string | null;
  retries?: number;
  createdAt?: Date;
  updatedAt?: Date;
};

export type BatchUpdate = Partial<Omit<BatchInsert, "id">>;

export type BatchWithUser = BatchEntity & { user: UserEntity };

export type RecentBatch = {
  id: string;
  ietfCode: string;
  resourceType: string;
  user: { username: string };
};

export type VerseEntity = {
  id: number;
  bookCode: string;
  chapter: number;
  verse: string;
  text: string;
  resourceId: number;
};

export type WordEntity = {
  id: number;
  word: string;
  batchId: string;
  verseId: number;
  createdAt: Date;
};

export type ModelEntity = {
  id: number;
  model: string;
  status: number;
  retries: number;
  wordId: number;
  createdAt: Date;
};

export type ReviewEntity = {
  pk: number;
  wordId: number;
  userId: number;
  correct: boolean;
};

export type WordWithModelsAndVerse = WordEntity & {
  models: ModelEntity[];
  verse: VerseEntity;
};

export type WordForReport = WordWithModelsAndVerse & {
  reviews: ReviewEntity[];
};

export type ResourceRef = {
  ietf: string;
  resourceType: string;
  name: string;
};

export type OAuthUpsert = {
  email: string;
  username: string;
  wacsUserId: number;
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  state: string;
};

export type VerseRef = { book: string; chapter: number; verse: string };

export type WordInsert = { word: string; verseId: number };

/** A word of the review pool joined to its verse and the user's own review. */
export type ReviewPoolRow = {
  word: string;
  review: ReviewEntity | null;
  book: string;
  chapter: number;
  verse: string;
  text: string;
};

export type StatusCount = { status: number; count: number };

/** How many words of each (unanimous) status to draw into the review pool. */
export type StatusLimit = { status: number; limit: number };

/** Consensus tallies over a batch's words. */
export type BatchTallies = {
  correct: number;
  incorrect: number;
  reviewNeeded: number;
  total: number;
  completed: number;
};

export type UserReviewCount = { userId: number; count: number };

// ---------------------------------------------------------------------------
// Repositories
// ---------------------------------------------------------------------------

export interface LanguagesRepo {
  /**
   * Ensure a languages row exists for this ietf code. If already present its
   * id is reused (and names refreshed); otherwise a row is created from BIEL
   * language info. Returns the language id.
   */
  upsert(info: LanguageInfo): Promise<number>;
  /** English name of a language (for the AI prompt), or "" if unknown. */
  getName(languageId: number): Promise<string>;
}

export interface ResourcesRepo {
  /** Resolve a resource id to its ietf code, resource type, and language name. */
  getRef(resourceId: number): Promise<ResourceRef | null>;
  /**
   * Resolve (ietf code, resource type) to an existing resource id, or null.
   * Used to locate a batch (which is keyed by its resource) from URL params.
   */
  getId(ietf: string, resourceType: string): Promise<number | null>;
  /** Ensure a resources row exists for (resourceType, languageId). Returns its id. */
  upsert(resourceType: string, languageId: number): Promise<number>;
}

export interface VersesRepo {
  /**
   * Insert verses for a resource, de-duplicating by (book, chapter, verse).
   * Existing refs get their text replaced; last occurrence wins.
   */
  insertMany(verses: Verse[], resourceId: number): Promise<void>;
  getByResource(resourceId: number): Promise<Verse[]>;
  /** Distinct book codes already stored for a resource. */
  getStoredBookCodes(resourceId: number): Promise<string[]>;
  /**
   * Map "book:chapter:verse" -> verse text for a resource, limited to the given
   * refs. Fetches only what the caller needs (avoids loading a whole Bible).
   */
  getTextsByRefs(resourceId: number, refs: VerseRef[]): Promise<Map<string, string>>;
  /** Map "book:chapter:verse" -> verse id for a resource. */
  getRefMap(resourceId: number): Promise<Map<string, number>>;
}

export interface WordsRepo {
  /** Insert words for a batch; (batch, word) duplicates are ignored. */
  insertMany(words: WordInsert[], batchId: string): Promise<void>;
  /** Ids of the given words in a batch that have no model rows yet. */
  fetchUnprocessedIds(words: { word: string }[], batchId: string): Promise<number[]>;
  /** Resolve (batchId, word) to its id. */
  findIdByWord(batchId: string, word: string): Promise<number | null>;
  /**
   * Words in a batch that still have at least one unchecked (status -1) model,
   * with their models and verse. Used by the AI-processing loop.
   */
  findUnprocessedForBatch(batchId: string, limit: number): Promise<WordWithModelsAndVerse[]>;
  /** All words in a batch with models, reviews and verse — for the CSV report. */
  findForReport(batchId: string): Promise<WordForReport[]>;
}

export interface ModelsRepo {
  /** Seed one status=-1 (unchecked) row per (word, model); existing rows are kept. */
  seed(wordIds: number[], models: string[]): Promise<void>;
  /** Write per-model statuses for the named words of a batch. */
  updateResults(batchId: string, results: ModelResult[]): Promise<void>;
  /**
   * Delete the models of a batch's words that still have an unchecked
   * (status -1) vote — used when pausing a batch.
   */
  deleteIncomplete(batchId: string): Promise<void>;
}

export interface BatchesRepo {
  findByResourceId(resourceId: number): Promise<BatchEntity | undefined>;
  /** Batch keyed by resource, with its creator (for stats / review screens). */
  findByResourceIdWithUser(resourceId: number): Promise<BatchWithUser | undefined>;
  /** Oldest batch currently being ingested, if any. */
  findIngesting(): Promise<BatchEntity | undefined>;
  /** Oldest batch pending AI processing, if any. */
  findPending(): Promise<BatchEntity | undefined>;
  create(values: BatchInsert): Promise<void>;
  updateById(id: string, values: BatchUpdate): Promise<void>;
  /** Stop a pending batch. Returns true if a row was affected. */
  pause(id: string): Promise<boolean>;
  deleteById(id: string): Promise<boolean>;
  /** Distinct batches whose words have model rows (ingested), with creator. */
  listRecent(): Promise<RecentBatch[]>;
}

export interface ReviewsRepo {
  upsert(review: { wordId: number; userId: number; correct: boolean }): Promise<void>;
  /** Delete every review for words in a batch. Returns true if any were removed. */
  deleteByBatch(batchId: string): Promise<boolean>;
  /**
   * Count the batch's "good" words (every model agrees on 0 or 1) per status.
   */
  countGoodWordsByStatus(batchId: string): Promise<StatusCount[]>;
  /**
   * The review pool for a batch/user: good words joined to their verse and the
   * user's existing review, ordered by word. With `limits`, each status is
   * sampled down to its limit in a per-user deterministic order; with null the
   * whole pool is returned.
   */
  fetchPool(
    batchId: string,
    userId: number,
    limits: StatusLimit[] | null,
  ): Promise<ReviewPoolRow[]>;
}

export interface UsersRepo {
  findByEmail(email: string): Promise<UserEntity | undefined>;
  /** A user whose login `state` is set and newer than `since` (unexpired). */
  findByFreshState(state: string, since: Date): Promise<UserEntity | undefined>;
  clearState(state: string): Promise<void>;
  upsertFromOAuth(values: OAuthUpsert): Promise<void>;
}

export interface StatsRepo {
  /** Consensus tallies for a batch (see `classifyVotes` for the rule). */
  getBatchTallies(batchId: string): Promise<BatchTallies>;
  /** Number of reviews each user has made in a batch. */
  getReviewCountsByUser(batchId: string): Promise<UserReviewCount[]>;
}

export type Repositories = {
  languages: LanguagesRepo;
  resources: ResourcesRepo;
  verses: VersesRepo;
  words: WordsRepo;
  models: ModelsRepo;
  batches: BatchesRepo;
  reviews: ReviewsRepo;
  users: UsersRepo;
  stats: StatsRepo;
};

export type DbDriver = "postgres" | "d1";
