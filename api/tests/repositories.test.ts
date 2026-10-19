import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { DRIVERS, DriverFixture } from "./helpers/drivers";
import { Repositories } from "../src/db/store";
import { Verse } from "../src/usfm";
import { computeBatchProgress } from "../src/services/stats.service";
import { sampleReviewWords } from "../src/services/review.service";

const LANG = {
  ietfCode: "xx",
  englishName: "Testish",
  nationalName: "Testisch",
  direction: "ltr",
};
const REF_LANG = { ...LANG, ietfCode: "en", englishName: "English" };

const OAUTH = {
  email: "a@example.com",
  username: "alice",
  wacsUserId: 1,
  accessToken: "at",
  refreshToken: "rt",
  tokenType: "bearer",
  state: "state-1",
};

function verses(n: number, book = "gen"): Verse[] {
  return Array.from({ length: n }, (_, i) => ({
    book,
    chapter: Math.floor(i / 50) + 1,
    verse: String((i % 50) + 1),
    text: `verse ${i} of ${book}`,
  }));
}

/** A batch with `n` words over `n` verses; returns ids needed by the tests. */
async function seedBatch(
  repos: Repositories,
  opts: { words?: number; models?: string[]; id?: string } = {},
) {
  const wordCount = opts.words ?? 6;
  const models = opts.models ?? ["m1", "m2"];
  const batchId = opts.id ?? "batch-1";

  await repos.users.upsertFromOAuth(OAUTH);
  const user = (await repos.users.findByEmail(OAUTH.email))!;
  const languageId = await repos.languages.upsert(LANG);
  const resourceId = await repos.resources.upsert("ulb", languageId);
  const refLanguageId = await repos.languages.upsert(REF_LANG);
  const refResourceId = await repos.resources.upsert("ulb", refLanguageId);

  const vs = verses(wordCount);
  await repos.verses.insertMany(vs, resourceId);
  await repos.verses.insertMany(
    vs.map((v) => ({ ...v, text: `ref ${v.text}` })),
    refResourceId,
  );
  const refMap = await repos.verses.getRefMap(resourceId);

  await repos.batches.create({
    id: batchId,
    userId: user.id,
    languageId,
    resourceId,
    refResourceId,
    models: JSON.stringify(models),
    ingesting: false,
    pending: true,
  });

  const words = vs.map((v, i) => ({
    word: `w${String(i).padStart(4, "0")}`,
    verseId: refMap.get(`${v.book}:${v.chapter}:${v.verse}`)!,
  }));
  await repos.words.insertMany(words, batchId);
  const wordIds = await repos.words.fetchIds(words, batchId);
  await repos.models.seed(batchId, wordIds, models);
  // As the ingestion service does: sets words_total on the batch.
  await repos.stats.recountTallies(batchId);

  return { user, languageId, resourceId, refResourceId, batchId, words, wordIds, models };
}

describe.each(DRIVERS)("%s repositories", (_name, make) => {
  let fx: DriverFixture;
  let repos: Repositories;

  beforeAll(async () => {
    fx = await make();
    repos = fx.repos;
  }, 60_000);

  afterAll(async () => {
    await fx.close();
  });

  beforeEach(async () => {
    // A batch cascades to words -> models/reviews, so deleting it isolates
    // each test; languages, resources and verses are upserted, so re-seeding
    // is idempotent.
    await repos.batches.deleteById("batch-1");
    await repos.batches.deleteById("batch-2");
  });

  describe("users", () => {
    it("upserts by email and reads back", async () => {
      await repos.users.upsertFromOAuth(OAUTH);
      await repos.users.upsertFromOAuth({ ...OAUTH, username: "alice2", state: "s2" });

      const user = await repos.users.findByEmail(OAUTH.email);
      expect(user?.username).toBe("alice2");
      expect(user?.state).toBe("s2");
      expect(user?.createdAt).toBeInstanceOf(Date);
      expect(user?.updatedAt).toBeInstanceOf(Date);
    });

    it("finds a fresh state and clears it", async () => {
      await repos.users.upsertFromOAuth({ ...OAUTH, state: "fresh" });
      const old = new Date(Date.now() - 60_000);
      const future = new Date(Date.now() + 60_000);

      expect(await repos.users.findByFreshState("fresh", old)).toBeDefined();
      expect(await repos.users.findByFreshState("fresh", future)).toBeUndefined();
      expect(await repos.users.findByFreshState("nope", old)).toBeUndefined();

      await repos.users.clearState("fresh");
      expect(await repos.users.findByFreshState("fresh", old)).toBeUndefined();
      expect((await repos.users.findByEmail(OAUTH.email))?.state).toBeNull();
    });
  });

  describe("languages & resources", () => {
    it("upserts idempotently and refreshes names", async () => {
      const id1 = await repos.languages.upsert(LANG);
      const id2 = await repos.languages.upsert({ ...LANG, englishName: "Renamed" });
      expect(id2).toBe(id1);
      expect(await repos.languages.getName(id1)).toBe("Renamed");
      expect(await repos.languages.getName(999_999)).toBe("");

      const r1 = await repos.resources.upsert("ulb", id1);
      const r2 = await repos.resources.upsert("ulb", id1);
      const r3 = await repos.resources.upsert("udb", id1);
      expect(r2).toBe(r1);
      expect(r3).not.toBe(r1);

      expect(await repos.resources.getId("xx", "ulb")).toBe(r1);
      expect(await repos.resources.getId("xx", "missing")).toBeNull();
      expect(await repos.resources.getRef(r3)).toEqual({
        ietf: "xx",
        resourceType: "udb",
        name: "Renamed",
      });
      expect(await repos.resources.getRef(999_999)).toBeNull();

      // Restore the name other tests expect.
      await repos.languages.upsert(LANG);
    });
  });

  describe("verses", () => {
    it("inserts in bulk (beyond one statement's params), dedupes and upserts text", async () => {
      const languageId = await repos.languages.upsert(LANG);
      const resourceId = await repos.resources.upsert("big", languageId);

      const many = verses(500, "exo");
      // A duplicate ref inside one call: last occurrence wins.
      many.push({ ...many[0], text: "overridden" });
      await repos.verses.insertMany(many, resourceId);

      const stored = await repos.verses.getByResource(resourceId);
      expect(stored).toHaveLength(500);
      expect(stored.find((v) => v.chapter === 1 && v.verse === "1")?.text).toBe(
        "overridden",
      );

      // Re-inserting replaces text instead of failing on the unique index.
      await repos.verses.insertMany([{ ...many[1], text: "again" }], resourceId);
      const refMap = await repos.verses.getRefMap(resourceId);
      expect(refMap.size).toBe(500);
      const texts = await repos.verses.getTextsByRefs(resourceId, [
        many[1],
        many[1],
        ...many.slice(2, 120),
        { book: "exo", chapter: 99, verse: "1" },
      ]);
      expect(texts.get("exo:1:2")).toBe("again");
      expect(texts.size).toBe(119);
      expect(await repos.verses.getStoredBookCodes(resourceId)).toEqual(["exo"]);
      expect(await repos.verses.getTextsByRefs(resourceId, [])).toEqual(new Map());
    });
  });

  describe("batches", () => {
    it("creates, finds, updates, pauses and deletes", async () => {
      const { user, resourceId, batchId } = await seedBatch(repos);

      const found = await repos.batches.findByResourceId(resourceId);
      expect(found?.id).toBe(batchId);
      expect(found?.pending).toBe(true);
      expect(found?.ingesting).toBe(false);
      expect(found?.apostropheIsSeparator).toBe(true);
      expect(found?.retries).toBe(0);
      expect(found?.createdAt).toBeInstanceOf(Date);

      const withUser = await repos.batches.findByResourceIdWithUser(resourceId);
      expect(withUser?.user.username).toBe(user.username);

      expect((await repos.batches.findPending())?.id).toBe(batchId);
      expect(await repos.batches.findIngesting()).toBeUndefined();

      await repos.batches.updateById(batchId, {
        ingesting: true,
        error: "boom",
        retries: 3,
        updatedAt: new Date(),
      });
      const updated = await repos.batches.findIngesting();
      expect(updated?.id).toBe(batchId);
      expect(updated?.error).toBe("boom");
      expect(updated?.retries).toBe(3);

      expect(await repos.batches.pause(batchId)).toBe(true);
      expect(await repos.batches.pause("missing")).toBe(false);
      const paused = await repos.batches.findByResourceId(resourceId);
      expect(paused?.pending).toBe(false);
      expect(paused?.error).toBeNull();

      expect(await repos.batches.deleteById(batchId)).toBe(true);
      expect(await repos.batches.deleteById(batchId)).toBe(false);
      expect(await repos.batches.findByResourceId(resourceId)).toBeUndefined();
    });

    it("lists only batches whose words have model rows", async () => {
      const { batchId, user } = await seedBatch(repos);
      // A batch with no words (not ingested yet) is not listed.
      await repos.batches.create({ id: "batch-2", userId: user.id });

      const recent = await repos.batches.listRecent();
      expect(recent).toEqual([
        { id: batchId, ietfCode: "xx", resourceType: "ulb", user: { username: "alice" } },
      ]);
    });
  });

  describe("words & models", () => {
    it("seeds unchecked models and resolves words", async () => {
      const { batchId, words, wordIds, models } = await seedBatch(repos, { words: 150 });
      expect(wordIds).toHaveLength(150);

      expect(await repos.words.fetchIds(words, batchId)).toEqual(wordIds);
      // Re-inserting the same words is a no-op.
      await repos.words.insertMany(words, batchId);

      const id = await repos.words.findIdByWord(batchId, words[3].word);
      expect(id).toBe(wordIds[3]);
      expect(await repos.words.findIdByWord(batchId, "nope")).toBeNull();

      const unprocessed = await repos.words.findUnprocessedForBatch(batchId, 7);
      expect(unprocessed).toHaveLength(7);
      expect(unprocessed[0].models.map((m) => m.model).sort()).toEqual(models);
      expect(unprocessed[0].models.every((m) => m.status === -1)).toBe(true);
      expect(unprocessed[0].verse.text).toContain("verse");
      expect(unprocessed[0].verse.chapter).toBe(1);
    });

    it("updates results per model in bulk and drops incomplete models on pause", async () => {
      const seeded = await seedBatch(repos, { words: 120 });
      const { batchId, words } = seeded;

      // More words than fit in one D1 statement, untrimmed words, mixed statuses.
      await repos.models.updateResults(batchId, [
        {
          model: "m1",
          retries: 2,
          results: words.map((w, i) => ({ word: ` ${w.word} `, status: i % 2 })),
        },
        {
          model: "m2",
          retries: 1,
          // Leave the last word unchecked for m2.
          results: words.slice(0, -1).map((w) => ({ word: w.word, status: 1 })),
        },
      ]);

      // The report lists reviewed words only; review three to inspect them.
      const { user } = seeded;
      for (const i of [119, 1, 0]) {
        await repos.reviews.upsert({ wordId: seeded.wordIds[i], userId: user.id, correct: true });
      }
      const report = await repos.words.findForReport(batchId);
      expect(report.map((w) => w.word)).toEqual([0, 1, 119].map((i) => words[i].word));
      expect(report[0].models.map((m) => [m.model, m.status, m.retries])).toEqual([
        ["m1", 0, 2],
        ["m2", 1, 1],
      ]);
      expect(report[1].models.map((m) => m.status)).toEqual([1, 1]);
      expect(report[2].models.map((m) => m.status)).toEqual([1, -1]);

      // Consensus is maintained on write: [0,1] -> review, [1,1] -> correct
      // and unanimous, [1,-1] -> still pending.
      expect(report[0]).toMatchObject({ consensus: "review", unanimous: false });
      expect(report[1]).toMatchObject({ consensus: "correct", unanimous: true });
      expect(report[2]).toMatchObject({ consensus: null, unanimous: false });

      const left = await repos.words.findUnprocessedForBatch(batchId, 50);
      expect(left.map((w) => w.word)).toEqual([words[119].word]);

      await repos.models.deleteIncomplete(batchId);
      const after = await repos.words.findForReport(batchId);
      expect(after[2].models).toEqual([]);
      expect(after[2]).toMatchObject({ consensus: null, unanimous: false });
      expect(after[0].models).toHaveLength(2);
      expect(after[0].consensus).toBe("review");
      expect(await repos.words.findUnprocessedForBatch(batchId, 50)).toEqual([]);
    });
  });

  describe("reviews & stats", () => {
    /** words 0..3 unanimous correct, 4..5 unanimous incorrect, 6 split, 7 unchecked */
    async function seedVotes() {
      const seeded = await seedBatch(repos, { words: 8 });
      const { batchId, words } = seeded;
      const vote = (status: number) => (w: { word: string }) => ({ word: w.word, status });
      await repos.models.updateResults(batchId, [
        {
          model: "m1",
          retries: 1,
          results: [
            ...words.slice(0, 4).map(vote(1)),
            ...words.slice(4, 6).map(vote(0)),
            { word: words[6].word, status: 1 },
          ],
        },
        {
          model: "m2",
          retries: 1,
          results: [
            ...words.slice(0, 4).map(vote(1)),
            ...words.slice(4, 6).map(vote(0)),
            { word: words[6].word, status: 0 },
          ],
        },
      ]);
      return seeded;
    }

    it("tallies consensus and review progress", async () => {
      const { batchId, user, wordIds, words } = await seedVotes();

      expect(await repos.stats.getBatchTallies(batchId)).toEqual({
        correct: 4,
        incorrect: 2,
        reviewNeeded: 1,
        total: 8,
        completed: 7,
      });

      // Legacy votes (2) count as processed without a consensus.
      await repos.models.updateResults(batchId, [
        { model: "m1", retries: 1, results: [{ word: words[7].word, status: 2 }] },
        { model: "m2", retries: 1, results: [{ word: words[7].word, status: 2 }] },
      ]);
      expect(await repos.stats.getBatchTallies(batchId)).toMatchObject({
        completed: 8,
        correct: 4,
      });

      // Tallies are counters on the batch, moved by deltas; a recount agrees.
      const live = await repos.stats.getBatchTallies(batchId);
      expect(await repos.stats.recountTallies(batchId)).toEqual(live);
      expect(live).toEqual({ correct: 4, incorrect: 2, reviewNeeded: 1, total: 8, completed: 8 });

      // Flipping a vote moves a word between buckets, not just into them.
      await repos.models.updateResults(batchId, [
        { model: "m1", retries: 0, results: [{ word: words[0].word, status: 0 }] },
      ]);
      expect(await repos.stats.getBatchTallies(batchId)).toMatchObject({
        correct: 3,
        reviewNeeded: 2,
        completed: 8,
      });
      // Unchecking a vote drops the word out of `completed`.
      await repos.models.updateResults(batchId, [
        { model: "m2", retries: 0, results: [{ word: words[1].word, status: -1 }] },
      ]);
      expect(await repos.stats.getBatchTallies(batchId)).toMatchObject({ correct: 2, completed: 7 });
      await repos.models.deleteIncomplete(batchId);
      expect(await repos.stats.getBatchTallies(batchId)).toEqual({
        correct: 2,
        incorrect: 2,
        reviewNeeded: 2,
        total: 8,
        completed: 7,
      });

      await repos.users.upsertFromOAuth({ ...OAUTH, email: "b@example.com", username: "bob" });
      const bob = (await repos.users.findByEmail("b@example.com"))!;
      await repos.reviews.upsert({ wordId: wordIds[0], userId: user.id, correct: true });
      await repos.reviews.upsert({ wordId: wordIds[0], userId: user.id, correct: false });
      await repos.reviews.upsert({ wordId: wordIds[1], userId: user.id, correct: true });
      await repos.reviews.upsert({ wordId: wordIds[0], userId: bob.id, correct: true });

      const counts = await repos.stats.getReviewCountsByUser(batchId);
      expect(counts.sort((a, b) => a.userId - b.userId)).toEqual([
        { userId: user.id, count: 2 },
        { userId: bob.id, count: 1 },
      ]);

      const progress = await computeBatchProgress(repos, batchId);
      expect(progress).toEqual({
        // Tallies as left by the vote changes above (read from the counters).
        correct: 2,
        incorrect: 2,
        review_needed: 2,
        reviewed: 2, // round((2 + 1) / 2)
        completed: 7,
        total: 8,
      });

      const reviewed = await repos.words.findForReport(batchId);
      expect(reviewed[0].reviews.map((r) => r.correct).sort()).toEqual([false, true]);

      // Word 7 (legacy votes) is only in the report once someone reviews it.
      expect(reviewed.map((w) => w.word)).toEqual([words[0].word, words[1].word]);
      await repos.reviews.upsert({ wordId: wordIds[7], userId: bob.id, correct: true });
      const withLegacy = await repos.words.findForReport(batchId);
      expect(withLegacy[2]).toMatchObject({ word: words[7].word, consensus: "none", unanimous: false });

      expect(await repos.reviews.deleteByBatch(batchId)).toBe(true);
      expect(await repos.reviews.deleteByBatch(batchId)).toBe(false);
      expect(await repos.stats.getReviewCountsByUser(batchId)).toEqual([]);
    });

    it("draws the review pool once and serves it from the sampled flag", async () => {
      const { batchId, user, words, wordIds } = await seedVotes();

      expect(
        (await repos.reviews.countGoodWordsByStatus(batchId)).sort(
          (a, b) => a.status - b.status,
        ),
      ).toEqual([
        { status: 0, count: 2 },
        { status: 1, count: 4 },
      ]);
      expect(await repos.reviews.countSampled(batchId)).toBe(0);
      expect(await repos.reviews.fetchPool(batchId, user.id)).toEqual([]);

      // Whole pool (no sampling needed).
      await repos.reviews.markSampled(batchId, null);
      expect(await repos.reviews.countSampled(batchId)).toBe(6);
      await repos.reviews.upsert({ wordId: wordIds[2], userId: user.id, correct: false });

      const whole = await repos.reviews.fetchPool(batchId, user.id);
      expect(whole.map((r) => r.word)).toEqual(words.slice(0, 6).map((w) => w.word));
      expect(whole[2].review?.correct).toBe(false);
      expect(whole[0].review).toBeNull();
      expect(whole[0]).toMatchObject({ book: "gen", chapter: 1, verse: "1" });
      expect(whole[0].text).toContain("verse 0");

      // Changing votes clears the flag; the pool is redrawn with limits.
      await repos.models.updateResults(batchId, [
        { model: "m1", retries: 0, results: [{ word: words[7].word, status: 1 }] },
      ]);
      expect(await repos.reviews.countSampled(batchId)).toBe(0);

      const limits = [
        { status: 1, limit: 2 },
        { status: 0, limit: 1 },
      ];
      await repos.reviews.markSampled(batchId, limits);
      const sampled = await repos.reviews.fetchPool(batchId, user.id);
      expect(sampled).toHaveLength(3);
      const correctWords = words.slice(0, 4).map((w) => w.word);
      const incorrectWords = words.slice(4, 6).map((w) => w.word);
      expect(sampled.filter((r) => correctWords.includes(r.word))).toHaveLength(2);
      expect(sampled.filter((r) => incorrectWords.includes(r.word))).toHaveLength(1);
      expect(sampled.map((r) => r.word)).toEqual([...sampled.map((r) => r.word)].sort());

      // A second draw is a no-op, so concurrent first requests cannot double the pool.
      await repos.reviews.markSampled(batchId, limits);
      await repos.reviews.markSampled(batchId, null);
      expect(await repos.reviews.countSampled(batchId)).toBe(3);
      expect((await repos.reviews.fetchPool(batchId, user.id)).map((r) => r.word)).toEqual(
        sampled.map((r) => r.word),
      );

      // Pausing (deleteIncomplete) only resets words that lost their models;
      // the drawn pool is made of complete words and survives.
      await repos.models.deleteIncomplete(batchId);
      expect(await repos.reviews.countSampled(batchId)).toBe(3);
      // Empty limits draw nothing.
      await repos.models.updateResults(batchId, [
        { model: "m1", retries: 0, results: [{ word: words[0].word, status: 1 }] },
      ]);
      expect(await repos.reviews.countSampled(batchId)).toBe(0);
      await repos.reviews.markSampled(batchId, []);
      expect(await repos.reviews.countSampled(batchId)).toBe(0);
    });

    it("adding a model re-opens processed words", async () => {
      const { batchId, words, wordIds } = await seedVotes();
      await repos.reviews.markSampled(batchId, null);
      expect(await repos.stats.getBatchTallies(batchId)).toMatchObject({ completed: 7 });

      // Re-ingest with a third model: every word gets an unchecked vote.
      await repos.models.seed(batchId, wordIds, ["m1", "m2", "m3"]);
      const pending = await repos.words.findUnprocessedForBatch(batchId, 50);
      expect(pending.map((w) => w.word)).toEqual(words.map((w) => w.word));
      expect(pending[0].models).toHaveLength(3);
      expect(pending[0]).toMatchObject({ consensus: null, unanimous: false, sampled: false });
      expect(await repos.reviews.countSampled(batchId)).toBe(0);
      expect(await repos.stats.recountTallies(batchId)).toMatchObject({ completed: 0, total: 8 });
    });

    it("sampleReviewWords draws lazily on the first request", async () => {
      const { batchId, user, words, wordIds } = await seedVotes();
      await repos.reviews.upsert({ wordId: wordIds[2], userId: user.id, correct: false });

      const { output, progress } = await sampleReviewWords(repos, batchId, user.id);
      expect(output).toHaveLength(6);
      expect(output[2]).toEqual({
        word: words[2].word,
        ref: "gen:1:3",
        text: expect.stringContaining("verse 2"),
        correct: false,
        results: [],
      });
      expect(progress.reviewed).toBe(1);
      expect(progress.total).toBe(6);
      expect(await repos.reviews.countSampled(batchId)).toBe(6);

      // Another user reads the same pool, with their own (empty) reviews.
      const again = await sampleReviewWords(repos, batchId, user.id + 1);
      expect(again.output.map((w) => w.word)).toEqual(output.map((w) => w.word));
      expect(again.progress.reviewed).toBe(0);
    });
  });

  describe("cascades", () => {
    it("deleting a batch removes its words, models and reviews", async () => {
      const { batchId, user, wordIds } = await seedBatch(repos, { words: 3 });
      await repos.reviews.upsert({ wordId: wordIds[0], userId: user.id, correct: true });
      await repos.batches.deleteById(batchId);
      expect(await repos.words.findForReport(batchId)).toEqual([]);
      expect(await repos.stats.getReviewCountsByUser(batchId)).toEqual([]);
      expect(await repos.stats.getBatchTallies(batchId)).toEqual({
        correct: 0,
        incorrect: 0,
        reviewNeeded: 0,
        total: 0,
        completed: 0,
      });
    });
  });
});
